# PetPlace website

Built from the PETPLACE Figma design. Front end: HTML, CSS and JavaScript. Back end: Java (Spring Boot).

```
petplace/
  frontend/   the website (open home.html through a web server, see below)
  backend/    Java Spring Boot API that also serves the website
```

## Run it

You need Java 17 or newer and Maven.

```
cd backend
mvn spring-boot:run
```

Then open http://localhost:8080 (the splash page). Run it from the `backend` folder: it serves `../frontend`
and reads the catalogue from `../frontend/data/catalog.json`.

Front end only: `cd frontend && python3 -m http.server 8000`, then open http://localhost:8000. Breeds, shop and
puppies work from the catalogue file; sign in, sign up, orders, reviews, newsletter and contact need the Java server.

## Pages (from the Figma screens)

Splash, Sign In, Create an Account, Home, Breeds, breed detail, Shop by Category, product list, product detail,
Checkout (cart, delivery address, delivery method, payment, order confirmation, success), Rating & Reviews,
Newsletter, Contact Us, and Puppies Available (the nav item had no screen in the design, so it is a simple list).

## API

| Method | Path | Purpose |
|---|---|---|
| GET | /api/breeds, /api/breeds/{slug} | Breeds |
| GET | /api/categories, /api/products?category=, /api/products/{id} | Shop |
| GET | /api/puppies | Puppies available |
| POST | /api/auth/register, /api/auth/login | Accounts (passwords hashed with BCrypt) |
| POST | /api/orders | Place an order (prices and totals are calculated on the server; 15% VAT is included in the total) |
| GET, POST | /api/reviews | Ratings and reviews |
| POST | /api/newsletter, /api/contact | Subscribe, send a message |

## Things to know

- Data is kept in memory, so accounts, orders and reviews are lost when the server restarts. Replace the
  collections in `DataStore.java` with a database (Spring Data JPA) for real use.
- Payments are not processed. The payment step only records the method chosen.
- Google and Apple sign-in buttons are in the design but not connected.
- The catalogue prices and the products outside Beds and Blankets are sample data. Edit `frontend/data/catalog.json`.
- Photos: see `frontend/images/README.md` for the file names to export from Figma.
