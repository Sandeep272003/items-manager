package com.example.itemmanager.repository;

import com.example.itemmanager.model.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class ItemRepository {

    private final File dataFile;
    private final ObjectMapper mapper;
    private final AtomicLong idGen = new AtomicLong(1);
    private final Object lock = new Object();

    public ItemRepository() {
        File dataDir = new File("data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "items.json");

        // Configure ObjectMapper with JavaTimeModule
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Accept numeric timestamps and also ISO strings
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, true);
        // For writing, prefer ISO strings (optional)
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Ensure file exists and initialize
        if (!dataFile.exists()) {
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, Collections.emptyList());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create data file", e);
            }
        }

        // initialize id generator from existing data (tolerant read)
        try {
            List<Item> items = readAllInternal();
            long max = items.stream().map(Item::getId).filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L);
            idGen.set(max + 1);
        } catch (Exception ignored) {}
    }

    /**
     * Robust read: try normal Jackson mapping first; if it fails (malformed or unexpected date format),
     * attempt a tolerant parse that handles numeric epoch seconds (with fractional part) for createdAt/updatedAt.
     */
    private List<Item> readAllInternal() {
        try {
            byte[] bytes = Files.readAllBytes(dataFile.toPath());
            if (bytes == null || bytes.length == 0) return new ArrayList<>();
            // Try direct mapping
            return mapper.readValue(bytes, new TypeReference<List<Item>>() {});
        } catch (JsonProcessingException jpe) {
            // Malformed or date type issue: attempt tolerant fallback
            try {
                String text = Files.readString(dataFile.toPath());
                JsonNode root = mapper.readTree(text);
                if (!root.isArray()) return new ArrayList<>();
                List<Item> items = new ArrayList<>();
                for (JsonNode node : root) {
                    Item it = new Item();
                    if (node.has("id") && node.get("id").canConvertToLong()) it.setId(node.get("id").longValue());
                    if (node.has("name")) it.setName(node.get("name").asText(null));
                    if (node.has("description")) it.setDescription(node.get("description").asText(null));
                    if (node.has("price")) it.setPrice(node.get("price").asDouble(0.0));
                    // createdAt/updatedAt: accept ISO string or numeric epoch seconds (with fraction)
                    it.setCreatedAt(parseInstantNode(node.get("createdAt")));
                    it.setUpdatedAt(parseInstantNode(node.get("updatedAt")));
                    items.add(it);
                }
                // After successful fallback parse, rewrite file in canonical format (ISO instants)
                writeAllInternal(items);
                return items;
            } catch (Exception ex) {
                // Backup broken file and reset to empty list
                try {
                    File backup = new File(dataFile.getParentFile(), "items.json.broken." + System.currentTimeMillis());
                    Files.copy(dataFile.toPath(), backup.toPath());
                    mapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, new ArrayList<>());
                    System.err.println("Backed up malformed items.json to: " + backup.getAbsolutePath());
                    return new ArrayList<>();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to recover from malformed items.json", e);
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to read items", ioe);
        }
    }

    private Instant parseInstantNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            if (node.isNumber()) {
                // numeric epoch: could be seconds with fraction (e.g., 167... .123)
                double d = node.doubleValue();
                long secs = (long) Math.floor(d);
                long nanos = (long) Math.round((d - secs) * 1_000_000_000L);
                return Instant.ofEpochSecond(secs, nanos);
            } else if (node.isTextual()) {
                String s = node.asText();
                // try ISO parse
                try {
                    return Instant.parse(s);
                } catch (Exception ex) {
                    // maybe numeric string
                    try {
                        double d = Double.parseDouble(s);
                        long secs = (long) Math.floor(d);
                        long nanos = (long) Math.round((d - secs) * 1_000_000_000L);
                        return Instant.ofEpochSecond(secs, nanos);
                    } catch (Exception ex2) {
                        return null;
                    }
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void writeAllInternal(List<Item> items) {
        try {
            // write to temp file then move atomically
            File tmp = new File(dataFile.getParentFile(), "items.json.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp, items);
            Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write items", e);
        }
    }

    public List<Item> findAll() {
        synchronized (lock) {
            return readAllInternal();
        }
    }

    public Optional<Item> findById(Long id) {
        synchronized (lock) {
            return readAllInternal().stream().filter(i -> Objects.equals(i.getId(), id)).findFirst();
        }
    }

    public Item save(Item item) {
        synchronized (lock) {
            List<Item> items = readAllInternal();
            if (item.getId() == null) {
                item.setId(idGen.getAndIncrement());
                item.setCreatedAt(Instant.now());
                item.setUpdatedAt(Instant.now());
                items.add(item);
            } else {
                boolean found = false;
                for (int i = 0; i < items.size(); i++) {
                    if (Objects.equals(items.get(i).getId(), item.getId())) {
                        item.setUpdatedAt(Instant.now());
                        item.setCreatedAt(items.get(i).getCreatedAt());
                        items.set(i, item);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    item.setCreatedAt(Instant.now());
                    item.setUpdatedAt(Instant.now());
                    items.add(item);
                }
            }
            writeAllInternal(items);
            return item;
        }
    }

    public boolean delete(Long id) {
        synchronized (lock) {
            List<Item> items = readAllInternal();
            List<Item> filtered = items.stream().filter(i -> !Objects.equals(i.getId(), id)).collect(Collectors.toList());
            if (filtered.size() == items.size()) return false;
            writeAllInternal(filtered);
            return true;
        }
    }

    public Map<String, Object> findWithQuery(String q, int page, int size, String sort) {
        synchronized (lock) {
            List<Item> items = readAllInternal();
            if (q != null && !q.isBlank()) {
                String qq = q.toLowerCase();
                items = items.stream().filter(i ->
                        (i.getName() != null && i.getName().toLowerCase().contains(qq)) ||
                        (i.getDescription() != null && i.getDescription().toLowerCase().contains(qq))
                ).collect(Collectors.toList());
            }
            if (sort != null && !sort.isBlank()) {
                String[] parts = sort.split(",");
                String field = parts[0];
                String dir = parts.length > 1 ? parts[1] : "asc";
                Comparator<Item> cmp = Comparator.comparing(Item::getId, Comparator.nullsLast(Comparator.naturalOrder()));
                if ("name".equalsIgnoreCase(field)) cmp = Comparator.comparing(i -> Optional.ofNullable(i.getName()).orElse(""));
                else if ("price".equalsIgnoreCase(field)) cmp = Comparator.comparingDouble(Item::getPrice);
                else if ("createdAt".equalsIgnoreCase(field)) cmp = Comparator.comparing(i -> Optional.ofNullable(i.getCreatedAt()).orElse(Instant.EPOCH));
                if ("desc".equalsIgnoreCase(dir)) cmp = cmp.reversed();
                items = items.stream().sorted(cmp).collect(Collectors.toList());
            }
            int total = items.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            List<Item> pageItems = items.subList(from, to);
            Map<String, Object> result = new HashMap<>();
            result.put("items", pageItems);
            result.put("page", page);
            result.put("size", size);
            result.put("total", total);
            return result;
        }
    }
}
