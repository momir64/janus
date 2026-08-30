import { h, mount } from "../lib/dom";
import { navigate } from "../lib/router";
import { api } from "../lib/api";
import { button } from "../components/button";
import { appNav } from "../components/nav";
import { passkeyCard } from "../components/passkeyCard";
import { confirmDialog } from "../components/confirmDialog";
import type { Passkey } from "../types";

export async function settingsPage(): Promise<Node> {
  const list = h("div", { class: "settings-page__passkeys" });

  async function loadPasskeys(): Promise<void> {
    renderPasskeys(await api.auth.listCredentials().catch(() => [] as Passkey[]));
  }

  function renderPasskeys(passkeys: Passkey[]): void {
    mount(
      list,
      ...passkeys.map((passkey) =>
        passkeyCard({
          passkey,
          onDelete: () =>
            confirmDialog(["Are you sure you want to delete", "this passkey?"], async () => {
              await api.auth.deleteCredential(passkey.id);
              await loadPasskeys();
            }),
        })
      )
    );
  }

  function deleteAccount(): void {
    // Figma 146:133 sets these as three lines; passed as segments so they
    // only break apart when the dialog is too narrow to hold them.
    confirmDialog(
      [
        "This action will permanently and irreversibly",
        "delete all data associated with this account.",
        "Are you sure you want to proceed?",
      ],
      async () => {
        await api.auth.deleteAccount();
        navigate("/");
      },
      { wide: true }
    );
  }

  // Every tab used to route to a bare "/app", which always opened Files —
  // so Notes landed on Files, and Settings navigated away from itself.
  const [top, bottom] = appNav({
    active: "settings",
    onTabChange: (tab) => {
      if (tab === "settings") return; // already here
      navigate(`/app?tab=${tab}`);
    },
  });

  const page = h(
    "div",
    {},
    top,
    h(
      "div",
      { class: "settings-page" },
      // Desktop 145:14 vs mobile 157:20 word this differently.
      h(
        "h2",
        { class: "settings-page__heading" },
        h("span", { class: "u-desktop-only" }, "Passkeys associated with this account"),
        h("span", { class: "u-mobile-only" }, "Associated passkeys")
      ),
      h("div", { class: "settings-page__rule" }),
      h(
        "div",
        { class: "settings-page__layout" },
        list,
        h(
          "div",
          { class: "settings-page__danger" },
          button({ label: "DELETE ACCOUNT", variant: "framed", danger: true, onClick: deleteAccount })
        )
      )
    ),
    bottom
  );

  void loadPasskeys();
  return page;
}
