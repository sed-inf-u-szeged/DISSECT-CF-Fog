document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".nav-list-expander").forEach((button) => {
    const parent = button.closest(".nav-list-item");

    if (parent && !parent.classList.contains("active")) {
      parent.classList.add("active");
    }

    button.setAttribute("aria-expanded", "true");
  });
});