async function loadText(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url} -> ${r.status}`);
  return r.text();
}
function compileAlpineTree(el) {
  if (window.Alpine?.initTree) Alpine.initTree(el);
  else setTimeout(() => compileAlpineTree(el), 0);
}

window.shell = function () {
  return {
    page: "chat",
    async go(p) {
      this.page = p;
      const html = await loadText(`/views/${p}.html`);
      this.$refs.outlet.innerHTML = html;
      compileAlpineTree(this.$refs.outlet); // IMPORTANT
      history.replaceState({}, "", `#${p}`);
    },
    async init() {
      const p = location.hash?.slice(1) || "chat";
      await this.go(p);
    },
  };
};
