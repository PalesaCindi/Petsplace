/* PetPlace front end — plain JavaScript, no build step.
   Talks to the Java (Spring Boot) back end under /api. Catalogue data (breeds,
   products, puppies) falls back to data/catalog.json if the back end is not running. */
(function () {
  "use strict";

  /* ---------- helpers ---------- */
  const API_BASE = location.port === "8080" ? "" : "http://localhost:8080";
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const esc = (s) =>
    String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const money = (n) => "R" + Number(n).toFixed(n % 1 ? 2 : 0).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  const img = (file, alt, cls = "") =>
    `<img class="${cls}" src="images/${esc(file)}" alt="${esc(alt)}" loading="lazy" onerror="this.onerror=null;this.src='images/placeholder.svg'">`;
  const param = (name) => new URLSearchParams(location.search).get(name);

  function store(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
  }
  function save(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ }
  }

  async function api(path, options = {}) {
    const res = await fetch(API_BASE + path, {
      headers: { "Content-Type": "application/json" },
      ...options,
      body: options.body ? JSON.stringify(options.body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || "Something went wrong. Please try again.");
    return data;
  }

  async function post(path, body) {
    try {
      return await api(path, { method: "POST", body });
    } catch (err) {
      if (err instanceof TypeError) {
        throw new Error("Can't reach the PetPlace server. Please start the Java back end and try again.");
      }
      throw err;
    }
  }

  let catalogPromise;
  function catalog() {
    if (!catalogPromise) {
      catalogPromise = (async () => {
        try {
          const [breeds, categories, products, puppies] = await Promise.all([
            api("/api/breeds"), api("/api/categories"), api("/api/products"), api("/api/puppies"),
          ]);
          return { breeds, categories, products, puppies };
        } catch {
          try {
            const res = await fetch("data/catalog.json");
            if (res.ok) return await res.json();
          } catch { /* e.g. page opened as a file: use the bundled copy below */ }
          if (window.PETPLACE_CATALOG) return window.PETPLACE_CATALOG;
          throw new Error("Could not load the catalogue. Please start the PetPlace server and open http://localhost:8080.");
        }
      })();
    }
    return catalogPromise;
  }

  function setMessage(el, text, ok) {
    el.textContent = text;
    el.className = "form-message " + (ok ? "ok" : "error");
  }

  /* ---------- cart ---------- */
  const Cart = {
    items: () => store("pp_cart", []),
    set(items) { save("pp_cart", items); updateCartCount(); },
    add(item) {
      const items = this.items();
      const found = items.find((i) => i.id === item.id && i.size === item.size);
      if (found) found.qty += item.qty; else items.push(item);
      this.set(items);
    },
    count: () => Cart.items().reduce((n, i) => n + i.qty, 0),
    subtotal: () => Cart.items().reduce((n, i) => n + i.price * i.qty, 0),
    clear() { this.set([]); },
  };
  function updateCartCount() {
    $$(".cart-count").forEach((el) => (el.textContent = Cart.count()));
  }

  /* ---------- header & footer ---------- */
  const NAV = [
    ["home", "home.html", "Home"],
    ["breeds", "breeds.html", "Breeds"],
    ["shop", "shop.html", "Shop"],
    ["newsletter", "newsletter.html", "Newsletter"],
    ["reviews", "reviews.html", "Reviews"],
    ["puppies", "puppies.html", "Puppies Available"],
    ["contact", "contact.html", "Contact Us"],
  ];
  const ACTIVE_ALIAS = { breed: "breeds", products: "shop", product: "shop", checkout: "shop" };

  function renderChrome() {
    const page = document.body.dataset.page;
    const active = ACTIVE_ALIAS[page] || page;
    const user = store("pp_user", null);
    const header = $("#site-header");
    if (header) {
      header.className = "site-header";
      header.innerHTML = `
        <div class="inner">
          <a class="brand" href="home.html">PetPlace</a>
          <nav class="nav" aria-label="Main">
            ${NAV.map(([key, href, label]) =>
              `<a href="${href}"${key === active ? ' class="active" aria-current="page"' : ""}>${label}</a>`).join("")}
            <a class="cart-link${page === "checkout" ? " active" : ""}" href="checkout.html">Cart<span class="cart-count">0</span></a>
            ${user
              ? `<a href="#" id="sign-out">Sign out (${esc(user.name.split(" ")[0])})</a>`
              : `<a href="signin.html">Sign In</a>`}
          </nav>
        </div>`;
      const out = $("#sign-out");
      if (out) out.addEventListener("click", (e) => { e.preventDefault(); localStorage.removeItem("pp_user"); location.href = "signin.html"; });
    }
    const footer = $("#site-footer");
    if (footer) {
      footer.className = "site-footer";
      footer.innerHTML = `&copy; ${new Date().getFullYear()} PetPlace &middot; Where Every Pet Feels at Home`;
    }
    updateCartCount();
  }

  /* ---------- pages ---------- */
  const pages = {};

  pages.splash = () => {
    setTimeout(() => { location.href = "signin.html"; }, 3500);
  };

  function authForm(kind) {
    const form = $("#auth-form");
    const msg = $("#form-message");
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const data = Object.fromEntries(new FormData(form));
      setMessage(msg, "One moment…", true);
      try {
        const result = await post(`/api/auth/${kind}`, data);
        save("pp_user", { name: result.name, email: result.email, token: result.token });
        location.href = "home.html";
      } catch (err) {
        setMessage(msg, err.message, false);
      }
    });
    $$("[data-social]").forEach((b) =>
      b.addEventListener("click", () => setMessage(msg, `${b.dataset.social} sign-in isn't set up yet. Please use email and password.`, false)));
  }
  pages.signin = () => authForm("login");
  pages.signup = () => authForm("register");

  pages.home = () => {};

  pages.breeds = async () => {
    const { breeds } = await catalog();
    $("#breed-grid").innerHTML = breeds
      .map((b) => `<a class="breed-tile" href="breed.html?slug=${encodeURIComponent(b.slug)}">
          <div class="photo">${img(b.image, b.name)}</div>${esc(b.name)}</a>`)
      .join("");
  };

  pages.breed = async () => {
    const { breeds } = await catalog();
    const breed = breeds.find((b) => b.slug === param("slug"));
    const box = $("#breed-detail");
    if (!breed) { box.innerHTML = `<p class="empty">We couldn't find that breed. <a href="breeds.html">Back to breeds</a></p>`; return; }
    document.title = `${breed.name} — PetPlace`;
    box.innerHTML = `
      <div class="photo">${img(breed.image, breed.name)}</div>
      <h1>${esc(breed.name)}s</h1>
      <p>${esc(breed.description)}</p>
      ${breed.slug === "pomeranian" ? img("breed-chart.jpg", "Pomeranian breed information chart", "chart") : ""}
      <p class="center"><a class="btn btn-primary" href="puppies.html">See puppies available</a></p>`;
  };

  pages.shop = async () => {
    const { categories } = await catalog();
    $("#cat-grid").innerHTML = categories
      .map((c) => `<a class="cat-tile" href="products.html?category=${encodeURIComponent(c.slug)}">
          <div class="photo">${img(c.image, c.name)}</div>${esc(c.name)}</a>`)
      .join("");
  };

  pages.products = async () => {
    const { categories, products } = await catalog();
    const slug = param("category") || "beds";
    const category = categories.find((c) => c.slug === slug) || categories[0];
    $("#category-name").textContent = category.name;
    document.title = `${category.name} — PetPlace`;
    $("#banner").innerHTML = `${img("banner-" + category.slug + ".jpg", category.name)}<span>${esc(category.name)}</span>`;
    const list = products.filter((p) => p.category === category.slug);
    $("#product-grid").innerHTML = list.length
      ? list.map((p) => `<a class="product-tile" href="product.html?id=${encodeURIComponent(p.id)}">
            <div class="photo">${img(p.image, p.name)}</div>${esc(p.name)}<span class="price">${money(p.price)}</span></a>`).join("")
      : `<p class="empty">No products in this category yet.</p>`;
  };

  pages.product = async () => {
    const { products } = await catalog();
    const p = products.find((x) => x.id === param("id"));
    const box = $("#product-detail");
    if (!p) { box.innerHTML = `<p class="empty">We couldn't find that product. <a href="shop.html">Back to shop</a></p>`; return; }
    document.title = `${p.name} — PetPlace`;
    let size = p.sizes[0];
    let qty = 1;
    box.innerHTML = `
      <div class="photo">${img(p.image, p.name)}</div>
      <div>
        <h1>${esc(p.name)}</h1>
        <div class="price">${money(p.price)}</div>
        <div class="field"><label id="size-label">Size</label>
          <div class="size-options" role="group" aria-labelledby="size-label">
            ${p.sizes.map((s, i) => `<button type="button" aria-pressed="${i === 0}" data-size="${esc(s)}">${esc(s)}</button>`).join("")}
          </div></div>
        <div class="field"><label id="qty-label">Quantity</label>
          <div class="qty" role="group" aria-labelledby="qty-label">
            <button type="button" id="minus" aria-label="Decrease quantity">−</button>
            <output id="qty" aria-live="polite">1</output>
            <button type="button" id="plus" aria-label="Increase quantity">+</button>
          </div></div>
        <button class="btn btn-primary" id="add" type="button">Add to Cart</button>
        <p class="form-message" id="form-message" role="status"></p>
      </div>`;
    $$(".size-options button", box).forEach((b) =>
      b.addEventListener("click", () => {
        size = b.dataset.size;
        $$(".size-options button", box).forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
      }));
    $("#minus").addEventListener("click", () => { qty = Math.max(1, qty - 1); $("#qty").textContent = qty; });
    $("#plus").addEventListener("click", () => { qty = Math.min(20, qty + 1); $("#qty").textContent = qty; });
    $("#add").addEventListener("click", () => {
      Cart.add({ id: p.id, name: p.name, price: p.price, image: p.image, size, qty });
      setMessage($("#form-message"), `Added ${qty} × ${p.name} (${size}) to your cart.`, true);
    });
  };

  pages.puppies = async () => {
    const { puppies, breeds } = await catalog();
    const byBreed = Object.fromEntries(breeds.map((b) => [b.slug, b]));
    $("#puppy-grid").innerHTML = puppies
      .map((p) => {
        const b = byBreed[p.breed] || { name: p.breed, image: "placeholder.svg" };
        return `<div class="breed-tile">
            <div class="photo">${img(b.image, b.name)}</div>
            ${esc(p.name)}<br><span class="muted">${esc(b.name)} · ${p.ageWeeks} weeks</span><br>
            <a href="contact.html?puppy=${encodeURIComponent(p.name + " (" + b.name + ")")}">Enquire</a></div>`;
      }).join("");
  };

  pages.reviews = async () => {
    const ratings = { dogs: 0, product: 0, page: 0 };
    $$(".stars").forEach((group) => {
      const key = group.dataset.key;
      for (let i = 1; i <= 5; i++) {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = "★";
        b.setAttribute("aria-label", `${i} star${i > 1 ? "s" : ""} for ${key}`);
        b.addEventListener("click", () => {
          ratings[key] = i;
          $$("button", group).forEach((x, idx) => x.classList.toggle("on", idx < i));
        });
        group.appendChild(b);
      }
    });
    const list = $("#review-list");
    const stars = (n) => "★".repeat(n) + "☆".repeat(5 - n);
    async function load() {
      try {
        const reviews = await api("/api/reviews");
        list.innerHTML = reviews.length
          ? reviews.map((r) => `<li><div><strong>${esc(r.author)}</strong> <span class="meta">Dogs ${stars(r.dogs)} · Product ${stars(r.product)} · Page ${stars(r.page)}</span></div>${r.comment ? `<p>${esc(r.comment)}</p>` : ""}</li>`).join("")
          : `<li class="muted">No reviews yet — be the first!</li>`;
      } catch { list.innerHTML = ""; }
    }
    load();
    $("#review-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#form-message");
      if (!ratings.dogs || !ratings.product || !ratings.page) { setMessage(msg, "Please give a star rating for dogs, product and page.", false); return; }
      const user = store("pp_user", null);
      try {
        await post("/api/reviews", {
          author: user ? user.name : "Guest",
          dogs: ratings.dogs, product: ratings.product, page: ratings.page,
          comment: $("#comment").value.trim(),
        });
        setMessage(msg, "Thank you for your review!", true);
        e.target.reset();
        $$(".stars button").forEach((b) => b.classList.remove("on"));
        Object.keys(ratings).forEach((k) => (ratings[k] = 0));
        load();
      } catch (err) { setMessage(msg, err.message, false); }
    });
  };

  pages.newsletter = () => {
    $("#newsletter-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#form-message");
      try {
        await post("/api/newsletter", Object.fromEntries(new FormData(e.target)));
        setMessage(msg, "You're subscribed. Welcome to the PetPlace family!", true);
        e.target.reset();
      } catch (err) { setMessage(msg, err.message, false); }
    });
  };

  pages.contact = () => {
    const puppy = param("puppy");
    if (puppy) $("#message").value = `Hi, I'd like to enquire about ${puppy}.`;
    $("#contact-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = $("#form-message");
      try {
        await post("/api/contact", Object.fromEntries(new FormData(e.target)));
        setMessage(msg, "Message sent. We'll get back to you soon.", true);
        e.target.reset();
      } catch (err) { setMessage(msg, err.message, false); }
    });
  };

  /* ----- checkout: cart → address → delivery → payment → confirm → done ----- */
  pages.checkout = () => {
    const STEPS = ["Shopping cart", "Your Delivery Address", "Delivery Method", "Payment & Order Summary", "Order Confirmation"];
    const ICONS = ["🛒", "📍", "📦", "💳", "✔"];
    const DELIVERY = { standard: 75, collect: 0 };
    const PAYMENTS = ["Credit card", "EFT", "Samsung Pay", "Apple Pay"];
    const state = { step: 0, address: store("pp_address", { street: "", city: "", postal: "", coords: "" }), method: "standard", payment: "" };
    const root = $("#checkout");

    const totals = () => {
      const sub = Cart.subtotal();
      const delivery = Cart.count() ? DELIVERY[state.method] : 0;
      const total = sub + delivery;
      return { sub, delivery, total, vat: Math.round((total * 15) / 115 * 100) / 100 };
    };

    function stepper(done) {
      return `<div class="steps" aria-label="Checkout progress">${ICONS.map((icon, i) =>
        `${i ? '<span class="bar"></span>' : ""}<span class="step ${i < state.step || done ? "done" : i === state.step ? "current" : ""}" aria-hidden="true">${icon}</span>`).join("")}</div>
        <p class="sr-only">Step ${state.step + 1} of 5: ${STEPS[state.step]}</p>`;
    }
    function totalsBlock() {
      const t = totals();
      return `<div class="totals">
        <div><span>Sub Total</span><span>${money(t.sub)}</span></div>
        <div><span>Delivery</span><span>${money(t.delivery)}</span></div>
        <div class="grand"><span>Total</span><span>${money(t.total)}</span></div>
        <div><span>VAT (incl. in Total)</span><span>${money(t.vat)}</span></div></div>`;
    }
    const actions = (back, nextLabel, nextId = "next") =>
      `<div class="step-actions">${back ? `<button class="btn" id="back" type="button">Back</button>` : "<span></span>"}
        <button class="btn btn-primary" id="${nextId}" type="button">${nextLabel}</button></div>`;

    function render() {
      const items = Cart.items();
      if (!items.length && state.step < 5) {
        root.innerHTML = `<div class="card empty">Your cart is empty.<br><br><a class="btn btn-primary" href="shop.html">Start shopping</a></div>`;
        return;
      }
      let body = "";
      if (state.step === 0) {
        body = `<p class="center">Thank you for choosing to shop with us. Please confirm the items and their quantities below and then click on <strong>Continue</strong>.</p>
          <div class="panel"><h3>Your Order</h3>
          <table class="cart-table"><thead><tr><th>Product</th><th class="num">Unit Price</th><th>Quantity</th><th class="num">Sub Total</th><th><span class="sr-only">Remove</span></th></tr></thead><tbody>
          ${items.map((it, i) => `<tr><td><span class="thumb">${img(it.image, it.name)}</span>${esc(it.name)}<br><small class="muted">${esc(it.size)}</small></td>
            <td class="num">${money(it.price)}</td>
            <td><span class="qty"><button type="button" data-dec="${i}" aria-label="Decrease quantity">−</button><output>${it.qty}</output><button type="button" data-inc="${i}" aria-label="Increase quantity">+</button></span></td>
            <td class="num">${money(it.price * it.qty)}</td>
            <td><button class="remove" type="button" data-rm="${i}" aria-label="Remove ${esc(it.name)}">✕</button></td></tr>`).join("")}
          </tbody></table>${totalsBlock()}</div>${actions(false, "Continue")}`;
      } else if (state.step === 1) {
        const a = state.address;
        body = `<div class="panel"><h3>Your Delivery Address</h3>
          <div class="field"><label for="street">Enter your street address</label><input id="street" type="text" autocomplete="street-address" value="${esc(a.street)}"></div>
          <div class="field"><label for="city">City / suburb</label><input id="city" type="text" autocomplete="address-level2" value="${esc(a.city)}"></div>
          <div class="field"><label for="postal">Postal code</label><input id="postal" type="text" inputmode="numeric" autocomplete="postal-code" value="${esc(a.postal)}"></div>
          <button class="loc-link" id="locate" type="button">📍 Use my current location</button>
          <p class="muted" id="loc-note">${a.coords ? "Location saved: " + esc(a.coords) : ""}</p>
          <p class="form-message" id="form-message" role="alert"></p></div>
          ${actions(true, "Save &amp; Continue")}`;
      } else if (state.step === 2) {
        body = `<div class="panel"><h3>Delivery Method</h3>
          <label class="option ${state.method === "standard" ? "selected" : ""}"><input type="radio" name="method" value="standard" ${state.method === "standard" ? "checked" : ""}>
            <strong>Standard Delivery</strong><span class="cost">R75</span><small>Earliest delivery 3 – 6 business days.</small></label>
          <label class="option ${state.method === "collect" ? "selected" : ""}"><input type="radio" name="method" value="collect" ${state.method === "collect" ? "checked" : ""}>
            <strong>Click 'n Collect</strong><span class="cost">FREE</span><small>Available for collection in store in 1 – 2 business days.</small></label></div>
          ${actions(true, "Continue")}`;
      } else if (state.step === 3) {
        body = `<div class="panel"><h3>Select a payment method</h3>
          ${PAYMENTS.map((p) => `<label class="pay-option"><input type="radio" name="payment" value="${p}" ${state.payment === p ? "checked" : ""}>${p}</label>`).join("")}
          <p class="form-message" id="form-message" role="alert"></p></div>
          ${totalsBlock()}${actions(true, "Continue")}`;
      } else if (state.step === 4) {
        body = `<div class="panel"><h3>Order Confirmation</h3>
          <table class="cart-table"><thead><tr><th>Product</th><th>Quantity</th><th class="num">Price</th></tr></thead><tbody>
          ${items.map((it) => `<tr><td>${esc(it.name)} <small class="muted">(${esc(it.size)})</small></td><td>${it.qty}</td><td class="num">${money(it.price * it.qty)}</td></tr>`).join("")}</tbody></table>
          ${totalsBlock()}
          <p><strong>Delivery:</strong> ${state.method === "collect" ? "Click 'n Collect" : "Standard delivery to " + esc([state.address.street, state.address.city, state.address.postal].filter(Boolean).join(", "))}<br>
          <strong>Payment:</strong> ${esc(state.payment)}</p>
          <p class="form-message" id="form-message" role="alert"></p></div>
          ${actions(true, "Place Order", "place")}`;
      } else {
        body = `<div class="success"><h2>Thank you for your order!</h2>
          <div class="badge"><svg viewBox="0 0 64 64" aria-hidden="true"><rect x="12" y="6" width="40" height="52" rx="4" fill="none" stroke="#111" stroke-width="3"/><path d="M22 24h20M22 34h12" stroke="#111" stroke-width="3" stroke-linecap="round"/><circle cx="44" cy="46" r="10" fill="#3cf21b" stroke="#111" stroke-width="3"/><path d="M39 46l4 4 7-8" fill="none" stroke="#111" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></svg></div>
          <p>Your order number is <strong>${esc(state.orderId)}</strong>.</p>
          <a class="btn btn-primary" href="shop.html">Continue shopping</a></div>`;
      }
      root.innerHTML = `<div class="card">${stepper(state.step === 5)}${state.step < 5 ? `<h2 class="step-title">${STEPS[state.step]}</h2>` : ""}${body}</div>`;
      bind();
    }

    function bind() {
      const next = $("#next"), back = $("#back");
      if (back) back.addEventListener("click", () => { state.step--; render(); });
      if (state.step === 0) {
        const edit = (i, d) => { const it = Cart.items(); it[i].qty = Math.max(1, Math.min(20, it[i].qty + d)); Cart.set(it); render(); };
        $$("[data-inc]").forEach((b) => b.addEventListener("click", () => edit(+b.dataset.inc, 1)));
        $$("[data-dec]").forEach((b) => b.addEventListener("click", () => edit(+b.dataset.dec, -1)));
        $$("[data-rm]").forEach((b) => b.addEventListener("click", () => { const it = Cart.items(); it.splice(+b.dataset.rm, 1); Cart.set(it); render(); }));
        next.addEventListener("click", () => { state.step = 1; render(); });
      } else if (state.step === 1) {
        $("#locate").addEventListener("click", () => {
          const note = $("#loc-note");
          if (!navigator.geolocation) { note.textContent = "Your browser doesn't support location."; return; }
          note.textContent = "Finding your location…";
          navigator.geolocation.getCurrentPosition(
            (pos) => { state.address.coords = `${pos.coords.latitude.toFixed(5)}, ${pos.coords.longitude.toFixed(5)}`; note.textContent = "Location saved: " + state.address.coords; },
            () => { note.textContent = "We couldn't get your location. Please type your address instead."; });
        });
        next.addEventListener("click", () => {
          const street = $("#street").value.trim(), city = $("#city").value.trim(), postal = $("#postal").value.trim();
          if (!street || !city) { setMessage($("#form-message"), "Please enter your street address and city.", false); return; }
          state.address = { ...state.address, street, city, postal };
          save("pp_address", state.address);
          state.step = 2; render();
        });
      } else if (state.step === 2) {
        $$("input[name=method]").forEach((r) => r.addEventListener("change", () => { state.method = r.value; render(); }));
        next.addEventListener("click", () => { state.step = 3; render(); });
      } else if (state.step === 3) {
        $$("input[name=payment]").forEach((r) => r.addEventListener("change", () => { state.payment = r.value; }));
        next.addEventListener("click", () => {
          if (!state.payment) { setMessage($("#form-message"), "Please select a payment method.", false); return; }
          state.step = 4; render();
        });
      } else if (state.step === 4) {
        $("#place").addEventListener("click", async (e) => {
          e.target.disabled = true;
          const user = store("pp_user", null);
          try {
            const order = await post("/api/orders", {
              email: user ? user.email : "",
              items: Cart.items().map((i) => ({ productId: i.id, size: i.size, quantity: i.qty })),
              address: state.address,
              deliveryMethod: state.method,
              paymentMethod: state.payment,
            });
            state.orderId = order.orderId;
            Cart.clear();
            state.step = 5; render();
          } catch (err) {
            e.target.disabled = false;
            setMessage($("#form-message"), err.message, false);
          }
        });
      }
    }
    render();
  };

  /* ---------- start ---------- */
  document.addEventListener("DOMContentLoaded", () => {
    renderChrome();
    const init = pages[document.body.dataset.page];
    if (init) Promise.resolve(init()).catch((err) => {
      const main = $("main") || document.body;
      main.insertAdjacentHTML("afterbegin", `<p class="empty form-message error" role="alert">${esc(err.message)}</p>`);
    });
  });
})();
