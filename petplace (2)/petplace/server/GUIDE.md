# Your PetPlace Java server, step by step

This server uses only what comes with Java. There is nothing to download and no Maven.
You need a **JDK, version 11 or newer** (17 or newer is best). Check with `javac -version`.

## Run it

**Windows:** double-click `run.bat` (or open a terminal in this folder and type `run.bat`).
**Mac / Linux:** in this folder run `sh run.sh`.

Wait for `PetPlace is running.`, then open **http://localhost:8080**. Keep the window open. Stop with Ctrl + C.

If the port is busy, start it on another one. Windows: `set PORT=9000` then `run.bat`. Mac/Linux: `PORT=9000 sh run.sh`.

## The files, in the order to read them

| Step | File | What it does |
|---|---|---|
| 1 | `src/Json.java` | Reads and writes JSON, the text format the website and server use to talk. |
| 2 | `src/Passwords.java` | Turns a password into a salted hash, so real passwords are never saved. |
| 3 | `src/Catalog.java` | Loads breeds, categories, products and puppies from `frontend/data/catalog.json`. |
| 4 | `src/Store.java` | Keeps accounts, reviews, newsletter sign-ups, messages and orders, and saves them to `data/store.json`. |
| 5 | `src/ApiError.java` | A small error type carrying a status code and a friendly message (your file, kept as it was). |
| 6 | `src/Api.java` | The routes: what happens for each `/api/...` address. |
| 7 | `src/StaticFiles.java` | Sends the website files (HTML, CSS, JavaScript, images) from the `frontend` folder. |
| 8 | `src/Main.java` | Start here when running. It creates the pieces above and starts the web server. |

## The routes

| Method | Address | What it does |
|---|---|---|
| GET | `/api/breeds` `/api/categories` `/api/puppies` | Lists from the catalogue |
| GET | `/api/products` or `/api/products?category=beds` | Products, optionally for one category |
| GET | `/api/products/bed-1` | One product |
| POST | `/api/auth/register` | Create an account (name, email, password of 8+ characters) |
| POST | `/api/auth/login` | Sign in |
| GET / POST | `/api/reviews` | Read reviews / add a review (three 1-5 star ratings and a comment) |
| POST | `/api/newsletter` | Subscribe |
| POST | `/api/contact` | Send a message |
| POST | `/api/orders` | Place an order. Prices come from the catalogue, never from the browser. 15% VAT is included in the total. |

## Add your own route (practice)

Open `src/Api.java`. In `route(...)`, inside the first `switch (path)` (the one under `if (method.equals("GET"))`),
add this line next to the other `case` lines:

```java
case "/api/hello": return ok(Map.of("message", "Hello from PetPlace"));
```

Stop the server (Ctrl + C), run it again, and open http://localhost:8080/api/hello in your browser.
You should see `{"message":"Hello from PetPlace"}`.

## Good to know

- Data is saved in `server/data/store.json`. Delete that file to start fresh.
- The server only answers requests from your own computer (127.0.0.1). That is safest while building.
- Payments are not processed. The payment step only records which method was chosen.
- For a real website you would add a database, HTTPS and sign-in sessions that expire.
