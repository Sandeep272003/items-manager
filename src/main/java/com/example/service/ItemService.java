package com.example.itemmanager.service;

import com.example.itemmanager.model.Item;
import com.example.itemmanager.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ItemService {

    private final ItemRepository repo;

    public ItemService(ItemRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> list(String q, int page, int size, String sort) {
        return repo.findWithQuery(q, page, size, sort);
    }

    public Optional<Item> get(Long id) {
        return repo.findById(id);
    }

    public Item create(Item item) {
        item.setId(null);
        return repo.save(item);
    }

    public Optional<Item> update(Long id, Item item) {
        return repo.findById(id).map(existing -> {
            existing.setName(item.getName());
            existing.setDescription(item.getDescription());
            existing.setPrice(item.getPrice());
            return repo.save(existing);
        });
    }

    public boolean delete(Long id) {
        return repo.delete(id);
    }
}
