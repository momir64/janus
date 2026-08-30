import { h, mount } from "../lib/dom";
import { navigate } from "../lib/router";
import { api } from "../lib/api";
import { button } from "../components/button";
import { appNav, logout } from "../components/nav";
import { passkeyCard } from "../components/passkeyCard";
import { confirmDialog } from "../components/confirmDialog";
import { alertDialog } from "../components/alertDialog";
import { messageLine } from "../components/messageLine";
import { isDesktop } from "../lib/breakpoint";
import { SETTINGS_MESSAGES } from "../lib/messages";
import { attachScrollbar } from "../lib/scrollbar";
import { closeCutEdge } from "../lib/scrollEdge";
import { setContentTab, type ContentTab } from "../lib/appTab";
import type { Passkey } from "../types";

export async function settingsPage(params: URLSearchParams): Promise<Node> {
  const list = h("div", { class: "settings-page__passkeys" });

  // Between Go back and Log out on desktop, where the nav row centres it; in
  // the page's top-right corner on mobile, where there is no such row.
  const message = messageLine({ className: "settings-page__message", fitPadding: 32 });

  let passkeyCount = 0;

  async function loadPasskeys(): Promise<void> {
    try {
      const passkeys = await api.auth.listCredentials();
      passkeyCount = passkeys.length;
      renderPasskeys(passkeys);
    } catch {
      message.show(SETTINGS_MESSAGES["36"]); // case 36
    }
  }

  function renderPasskeys(passkeys: Passkey[]): void {
    mount(
      list,
      ...passkeys.map((passkey) =>
        passkeyCard({
          passkey,
          onDelete: () => {
            // Case 33. Removing the only passkey leaves the account
            // reachable by email alone, so the warning is given before the
            // question and the outcome is spelled out after it.
            const last = passkeyCount === 1;
            // TODO: the backend does not say which credential the session is
            //  running on - listCredentials returns { id, algorithm } and the
            //  session's credentialId lives only in the refresh token. Have
            //  it flag the current one, and this reads it.
            const current = passkey.currentSession === true;
            const question = ["Are you sure you want to delete", "this passkey?"];
            // Re-segmented rather than prefixed: with the warning in front,
            // the copy reads as two lines rather than the question's own
            // two plus one. Below the width that holds them the segmenting
            // is dropped and it wraps as ordinary text, as everywhere else.
            const lastQuestion = [
              "This is your last passkey. Are you sure",
              "you want to delete this passkey?",
            ];
            // Deleting a credential revokes the refresh chain issued to it
            // (UserService.deleteCredential), so removing the one this
            // session is running on ends the session with it.
            // One string rather than segments: this one carries no breaks of
            // its own and is left to wrap wherever the frame runs out.
            const currentQuestion =
              "This passkey is used for the current session. Deleting it will " +
              "cause you to log out. Are you sure you want to delete this passkey?";

            confirmDialog(
              last ? lastQuestion : current ? currentQuestion : question,
              async () => {
                // TODO: case 34 - report a failed delete through message.show().
                await api.auth.deleteCredential(passkey.id);
                // The last passkey leaves email recovery as the only way
                // back in; any other current-session one leaves the
                // account's remaining passkeys.
                if (last || current) {
                  // Breaks differ with the frame, and the frame is settled
                  // by the time this opens - a resize while it is up leaves
                  // the copy broken for the width it was opened at.
                  const session = isDesktop() ? "41" : "41-mobile";
                  const told = last ? SETTINGS_MESSAGES["33"] : SETTINGS_MESSAGES[session];
                  alertDialog(told.split("\n"), () => void logout(), {
                    frame: last ? undefined : "session",
                  });
                  return;
                }
                await loadPasskeys(); // case 32 is silent: the list simply redraws
              },
              { frame: last ? "roomy" : current ? "session" : undefined }
            );
          },
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
        // TODO: case 35 - report a failed deletion through message.show().
        await api.auth.deleteAccount();
        navigate("/");
      },
      { frame: "wide" }
    );
  }

  const [top, bottom] = appNav({
    active: "settings",
    onTabChange: (tab) => {
      if (tab === "settings") return; // already here
      // The chosen list travels in memory, so "/" opens on it rather than
      // always falling back to Files.
      setContentTab(tab as ContentTab);
      navigate("/");
    },
  });

  const layout = h(
    "div",
    { class: "settings-page__layout" },
    list,
    h(
      "div",
      { class: "settings-page__danger" },
      button({ label: "DELETE ACCOUNT", variant: "framed", danger: true, onClick: deleteAccount })
    )
  );

  const content = h(
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
    layout
  );

  // The page itself never scrolls; what does depends on the layout. The nav
  // sits outside the centred column so its buttons keep the page's own 36px
  // corners.
  const frame = h("div", { class: "settings-scroll" }, top, content);

  // Same shell as the app page, so the page itself never scrolls.
  const page = h("div", { class: "app-shell" }, frame, bottom);

  // The desktop nav row centres the line between its two buttons; on mobile
  // that row is not rendered at all, so the line is placed from the page's
  // own corner instead.
  function placeMessage(): void {
    if (!page.isConnected) return; // a stale page's listener still firing
    const host = isDesktop() ? top : page;
    if (message.el.parentElement === host) return;
    if (host === top) top.insertBefore(message.el, top.lastElementChild);
    else page.append(message.el);
  }

  window.addEventListener("resize", placeMessage);
  requestAnimationFrame(() => {
    placeMessage();

    // TODO: development only - ?m=<case> shows that message where it will
    //  live, e.g. /settings?m=34. Remove once the settings messages are
    //  wired to their triggers.
    const preview = params.get("m");
    if (!preview) return;
    if (preview === "33") alertDialog(SETTINGS_MESSAGES["33"].split("\n"), () => void logout());
    else if (preview === "39") window.dispatchEvent(new CustomEvent("kredenac:session-expired"));
    else if (SETTINGS_MESSAGES[preview]) message.show(SETTINGS_MESSAGES[preview]);
  });

  // Desktop scrolls the cards alone, from the separator line down; mobile
  // scrolls them together with the delete button below them, since there the
  // two are stacked rather than side by side. Both scrollers exist at both
  // sizes and CSS decides which of them actually overflows - a bar whose
  // scroller does not is hidden by the script that draws it.
  attachScrollbar(list).classList.add("settings-page__cards");
  attachScrollbar(layout, { inset: 0 });

  // A card cut by the scroller's top edge loses its own top border with it.
  // Both scrollers get the rule that stands in for it; CSS drops whichever
  // of the two is not the one scrolling at the current width.
  closeCutEdge(list);
  closeCutEdge(layout, list);

  page.append(message.el);

  void loadPasskeys();
  return page;
}
