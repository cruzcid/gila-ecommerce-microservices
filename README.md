# Gila E-Commerce — Microservices

E-commerce platform built for the Gila code challenge, decomposed into independent Spring Boot microservices.

---

## Architecture

```
Browser / Frontend (React + Vite)
        │
        ▼  :80
   [ frontend ]   (Nginx)
        │
        ▼  /api/**  → :8080
  [ api-gateway ]  (Spring WebFlux reverse proxy)
   ┌────┴────┬────────┐
   ▼         ▼        ▼
product    order   payment   (Spring Boot services)
  :8081     :8082    :8083
   │           │       │
   PG        Mongo     PG
```

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 8080 | Reverse-proxies all `/api/**` to downstream services; owns CORS |
| **product-service** | 8081 | Product CRUD, CSV import, search, stock adjustment, import-log (MongoDB) |
| **order-service** | 8082 | Order lifecycle; calls product-service for product snapshot and stock |
| **payment-service** | 8083 | Simulated payment (always APPROVED); calls order-service to mark order PAID |
| **frontend** | 80 | React/TypeScript SPA served via Nginx |

### Databases

| Container   | Engine        | Purpose              | Host port |
|---          |---            |---                   |---        |
| `product-db`| PostgreSQL 16 | Products table       | 5433      |
| `order-db`  | PostgreSQL 16 | Orders + order items | 5434      |
| `mongodb`   | MongoDB 7     | Import logs          | 27017     |

### Inter-service communication

All calls are synchronous HTTP using Spring's `RestClient`:

- **order-service → product-service** — `GET /api/products/{id}` (price/name snapshot) and `PATCH /api/products/{id}/stock` (decrement stock)
- **payment-service → order-service** — `PATCH /api/orders/{id}/pay` (mark order PAID)

The api-gateway is a **custom WebFlux reverse proxy** (`ProxyController`) — no Spring Cloud Gateway dependency.

---

## Requirements fulfilled

| Requirement | How |
|---|---|
| Local DB (SQL + NoSQL) | PostgreSQL per service (`product_db`, `order_db`) + MongoDB for import logs |
| Product CRUD | `product-service` — `GET/POST/PUT/DELETE /api/products` |
| CSV import | `POST /api/products/import` (OpenCSV) |
| CSV columns | `name, sku, description, category, price, stock, weight_kg` (multipart POST) |
| Product search | `GET /api/products?search=...&category=...` |
| Purchase (fake payment) | `POST /api/payments/checkout` — simulated, always APPROVED |
| UI — CRUD | Admin page with table + modal form |
| UI — Search | Products page with search bar + category filter |
| UI — Purchase | Cart → Checkout page with fake card form + receipt |

---

## Sample CSV

The CSV file was first downloaded at 21 Jun 2026 at 8:27

CSV column order expected by the importer:

```
name, sku, description, category, price, stock, weight_kg
```

---

## Running with Docker Compose

Single command — starts all 7 containers (3 databases + 4 services + frontend):

```bash
docker compose up --build
```

To stop and remove all containers and volumes:

```bash
docker compose down -v
```

| URL                                         | What                         |
|---                                          |---                           |
| http://localhost                            | Frontend (React SPA)         |
| http://localhost:8080/actuator/health       | API Gateway health check     |
| http://localhost:8080/api/products          | Products API (via gateway)   |
| http://localhost:8080/api/orders            | Orders API (via gateway)     |
| http://localhost:8080/api/payments/checkout | Payment API (via gateway)    |
| http://localhost:8080/api/import-logs       | Import log API (via gateway) |

> **Note:** On the first cold build Docker downloads all Maven dependencies once (shared builder stage). Subsequent builds reuse the BuildKit cache and are much faster.

---

## Local Development (without Docker)

Start the infrastructure first:

```bash
# PostgreSQL for product-service
docker run -d -p 5433:5432 -e POSTGRES_DB=product_db -e POSTGRES_USER=gila -e POSTGRES_PASSWORD=gila123 postgres:16-alpine

# PostgreSQL for order-service
docker run -d -p 5434:5432 -e POSTGRES_DB=order_db -e POSTGRES_USER=gila -e POSTGRES_PASSWORD=gila123 postgres:16-alpine

# MongoDB
docker run -d -p 27017:27017 mongo:7-jammy
```

Then run each service from the `backend-microservices` directory:

```bash
cd backend-microservices
mvn spring-boot:run -pl product-service   # :8081
mvn spring-boot:run -pl order-service     # :8082
mvn spring-boot:run -pl payment-service   # :8083
mvn spring-boot:run -pl api-gateway       # :8080
```

Frontend:

```bash
cd frontend && npm install && npm run dev  # :5173
```

---

## Project structure

```
gila-ecommerce-microservices/
├── docker-compose.yml                ← single-command orchestration
├── frontend/                         ← React + Vite + TypeScript + TailwindCSS
│   ├── Dockerfile
│   └── src/
└── backend-microservices/            ← Maven multi-module project
    ├── Dockerfile                    ← shared multi-stage build (all 4 services)
    ├── pom.xml                       ← parent POM (Spring Boot 4.1.0, Java 21)
    ├── api-gateway/                  ← WebFlux reverse proxy, CORS
    ├── product-service/              ← JPA (PostgreSQL) + MongoDB
    ├── order-service/                ← JPA (PostgreSQL), RestClient → product-service
    └── payment-service/              ← Stateless, RestClient → order-service
```

