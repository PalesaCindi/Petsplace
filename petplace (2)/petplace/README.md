# PetPlace website

Built from the PETPLACE Figma design. Front end: HTML, CSS and JavaScript. Back end: Java (no libraries needed).

```
petplace/
  frontend/   the website pages, styles, scripts and catalogue data
  server/     the Java server (also serves the website). Start with server/GUIDE.md
  tools/      small helper scripts
```

## Run it

1. Install a JDK (version 11 or newer, 17+ best). Check with `javac -version`.
2. Open the `server` folder and run `run.bat` (Windows) or `sh run.sh` (Mac/Linux).
3. When it says `PetPlace is running.`, open http://localhost:8080 and keep the window open.

Full explanation of every file: `server/GUIDE.md`.

## Pages (from the Figma screens)

Splash, Sign In, Create an Account, Home, Breeds, breed detail, Shop by Category, product list, product detail,
Checkout (cart, delivery address, delivery method, payment, order confirmation, success), Rating & Reviews,
Newsletter, Contact Us, and Puppies Available (the nav item had no screen in the design, so it is a simple list).

## Things to know

- Accounts, reviews, orders, newsletter sign-ups and messages are saved in `server/data/store.json`.
- Payments are not processed. The payment step only records the method chosen.
- Google and Apple sign-in buttons are in the design but not connected.
- The prices and the products outside Beds and Blankets are sample data. Edit `frontend/data/catalog.json`,
  then run `python tools/sync_catalog.py` so the bundled copy (`frontend/js/catalog-data.js`) matches. That copy lets
  the breeds, shop and puppies pages show even when a page is opened without a server.
- Photos: see `frontend/images/README.md` for the file names to export from Figma.
