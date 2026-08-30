/**
 * Response messages, keyed by the case numbers from the message audit so a
 * key is traceable back to the exact failure it belongs to. The letters are
 * part of the key where one number covers two distinguishable cases.
 *
 * A "\n" marks where a message breaks when it is too wide for one line. The
 * line uses it only when it has to, so a wider viewport reads it unbroken.
 */
export const LOGIN_MESSAGES: Record<string, string> = {
  // Passkey sign-in.
  "2": "Something went wrong. Your device\nor browser blocked the sign-in.",
  "3a": "Passkey verification failed.\nSelected passkey is invalid.",
  "3b": "Passkey verification failed.\nPlease try again.",
  "4": "Connection failed. Couldn't\nreach the server.",
  "5": "Too many login attempts.\nTry again later.",
  "6": "Something went wrong\non our end.",
  "37": "Possible passkey cloning detected.\nThis passkey has been disabled.",

  // Email verification flow.
  "7a": "Entered email\naddress is invalid.",
  "7b": "Please enter your\nemail address.",
  "8": "We couldn't send the email.\nTry again later.",
  "9": "Too many verification requests.\nTry again later.",
  "10": "Connection failed. Couldn't\nreach the server.",
  "11a": "A recovery email will be sent\nif the account exists.",
  "11b": "Check your inbox for\nregistration link.",
};

/** Passkey registration, shown below the button on the valid verify page. */
export const VERIFY_MESSAGES: Record<string, string> = {
  "14": "Passkey registration\nwas cancelled.",
  "15": "A passkey for that account already\nexists on this device.",
  "16a": "Too many registration attempts.\nTry again later.",
  "16b": "Your device can't store a passkey\nsecurely. Try a different device.",
  "16c": "Your device doesn't\nsupport passkeys.",
  "16d": "The website is being served\nfrom an invalid domain.",
  "16e": "Something on this device or browser\nblocked the passkey setup.",
  "16f": "Something went wrong while\nregistering your passkey.",
  "16g": "Passkey registration\nwas cancelled.",
};

/**
 * Files. Shown in the slot the upload control's cancel link uses - inside
 * the dropzone on desktop, between the button and the list on mobile - so a
 * message and an upload in progress never need it at the same time.
 */
export const FILE_MESSAGES: Record<string, string> = {
  "18": "Selected file is too large.\nMaximum file size is 50MB.",
  "40": "Selected file's name is too long.\nMaximum length is 255 characters.",
  "19": "Upload failed. Try to\nupload the file again.",
  "20": "Upload failed. Try to\nupload the file again.",
  "21": "There was an error with\ndownloading {filename}.",
  "22": "File {filename}\nno longer exists.",
  "23": "Failed to delete\n{filename}.",
  "24": "Failed to load\nthe files.",
};

/**
 * Notes. 25 to 27 and 29 belong to the editor, above its title field; 28
 * and 30 to the column, below the button that opens it; 31 takes the
 * place of the list it could not load.
 */
export const NOTE_MESSAGES: Record<string, string> = {
  "25": "A note can't be\ncompletely empty.",
  "26": "The title is too long.\nTitle limit is 200 characters.",
  "27": "The body is too long.\nBody limit is 2000 characters.",
  "28": "Requested note\nno longer exists.",
  "29": "Failed to save\nthe note.",
  "30": "Failed to delete\nthe note.",
  "31": "Failed to load\nthe notes.",
};

/**
 * Settings. 34 to 36 are shown between the nav buttons on desktop and in
 * the page's top-right corner on mobile; 33 is a dialog, since it ends the
 * session and cannot be left unread.
 */
export const SETTINGS_MESSAGES: Record<string, string> = {
  "33": "You removed your last passkey.\nUse email recovery flow to log in again.",
  "41": "You removed your current session passkey.\nUse another passkey to log in again.",
  // The same copy over three lines, for the narrower frame.
  "41-mobile": "You removed your current session\npasskey. Use another passkey\nto log in again.",
  "34": "Failed to delete the passkey.",
  "35": "Failed to delete the account.",
  "36": "Failed to list your passkeys.",
};

/** Anywhere: the session ending under the reader. */
export const GLOBAL_MESSAGES: Record<string, string> = {
  "39": "You have been signed out.\nPlease sign in again.",
};
