import { readSettings, saveSettings } from "../app.js";

window.settingsPage = function () {
  const defaults = {
    token: "",
    host: location.hostname || "127.0.0.1",
    port: Number(location.port) || 8080,
    autoScroll: true,
  };

  return {
    form: { ...defaults },
    load() {
      this.form = { ...defaults, ...readSettings() };
    },
    save() {
      saveSettings(this.form);
      alert("Saved.");
    },
    reset() {
      this.form = { ...defaults };
      this.save();
    },
    init() {
      this.load();
    },
  };
};
