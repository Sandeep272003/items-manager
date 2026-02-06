Items Manager — Sample Java Backend
===================================

This project is a simple Java backend application implementing a RESTful API
for managing a collection of items. It was created as part of the Freelance
Java Developer sample task.

------------------------------------------------------------
Demo Deployment
------------------------------------------------------------
- Backend live link: https://items-manager-yvbd.onrender.com
- Items API endpoint: https://items-manager-yvbd.onrender.com/api/items
- Source code (GitHub): https://github.com/Sandeep272003/items-manager

------------------------------------------------------------
Features
------------------------------------------------------------
- Item Model:
  * id (Long, auto-generated)
  * name (String, required)
  * description (String, required)
  * price (double, optional)

- Data Storage:
  * In-memory ArrayList<Item> used to store items.

- Validation:
  * Required fields (name, description) are checked when adding items.

- RESTful API:
  * Endpoints to add new items and fetch items by ID.

------------------------------------------------------------
API Endpoints
------------------------------------------------------------
Base URL: https://items-manager-yvbd.onrender.com/api/items

1. Add a new item
   - POST /api/items
   - Request Body (JSON):
     {
       "name": "Aurora Wireless Headphones",
       "description": "Over-ear Bluetooth headphones with ANC",
       "price": 1725.0
     }
   - Response: 201 Created with the created item (including generated id).

2. Get a single item by ID
   - GET /api/items/{id}
   - Example: GET /api/items/1
   - Response: 200 OK with item JSON, or 404 Not Found if no item exists.

------------------------------------------------------------
How to Run Locally
------------------------------------------------------------
Prerequisites:
- Java 17+
- Maven 3.6+

Steps:
1. Clone repository
   git clone https://github.com/Sandeep272003/items-manager.git
   cd items-manager

2. Build
   mvn -DskipTests clean package

3. Run
   java -jar target/items-manager-1.0.0.jar

The server will start on port 8080.

Test with curl:
   curl -X POST http://localhost:8080/api/items \
     -H "Content-Type: application/json" \
     -d '{"name":"Test","description":"desc"}'

   curl http://localhost:8080/api/items/1

------------------------------------------------------------
Implementation Details
------------------------------------------------------------
- Framework: Spring Boot (REST controllers).
- Storage: In-memory ArrayList<Item>.
- Validation: Simple null/empty checks in the service layer.
- Comments: Code includes inline comments explaining logic.

