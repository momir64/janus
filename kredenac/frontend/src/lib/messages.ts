/**
 * Response messages, grouped by the surface that shows them. Each carries the
 * case number it was given in the message audit, so a message can be traced
 * back to the exact failure it belongs to and the TODOs that name a number
 * still lead somewhere.
 *
 * A "\n" marks where a message breaks when it is too wide for one line.
 * The line uses it only when it has to, so a wider viewport reads it unbroken.
 */

/** The login page's line, below the LOGIN / REGISTER pair. */
export const LOGIN_MESSAGES = {
  /** Case 2. */
  browserBlocked: "Something went wrong. Your device\nor browser blocked the sign-in.",
  /** Case 3a. */
  passkeyRejected: "Passkey verification failed.\nSelected passkey is invalid.",
  /** Case 3b. */
  passkeyRetry: "Passkey verification failed.\nPlease try again.",
  /** Case 37. */
  passkeyCloned: "Possible passkey cloning detected.\nThis passkey has been disabled.",
  /** Case 4 and 10. */
  noConnection: "Connection failed. Couldn't\nreach the server.",
  /** Case 5. */
  tooManyLogins: "Too many login attempts.\nTry again later.",
  /** Case 6. */
  serverError: "Something went wrong\non our end.",
  /** Case 7a. */
  emailInvalid: "Entered email\naddress is invalid.",
  /** Case 7b. */
  emailMissing: "Please enter your\nemail address.",
  /** Case 8. */
  emailNotSent: "We couldn't send the email.\nTry again later.",
  /** Case 9. */
  tooManyEmails: "Too many verification requests.\nTry again later.",
  /** Case 11a. */
  recoverySent: "A recovery email will be sent\nif the account exists.",
  /** Case 11b. */
  registrationSent: "Check your inbox for\nregistration link.",
} as const;

/** The verify page's line, below REGISTER NEW PASSKEY. */
export const VERIFY_MESSAGES = {
  /** Case 14 and 16g. */
  registrationCancelled: "Passkey registration\nwas cancelled.",
  /** Case 15. */
  passkeyExists: "A passkey for that account already\nexists on this device.",
  /** Case 16a. */
  tooManyAttempts: "Too many registration attempts.\nTry again later.",
  /** Case 16b. */
  deviceCannotStore: "Your device can't store a passkey\nsecurely. Try a different device.",
  /** Case 16c. */
  deviceUnsupported: "Your device doesn't\nsupport passkeys.",
  /** Case 16d. */
  invalidDomain: "The website is being served\nfrom an invalid domain.",
  /** Case 16e. */
  browserBlocked: "Something on this device or browser\nblocked the passkey setup.",
  /** Case 16f. */
  registrationFailed: "Something went wrong while\nregistering your passkey.",
} as const;

/**
 * Files. Shown in the slot the upload control's cancel link uses - inside the
 * dropzone on desktop, between the button and the list on mobile - except
 * listFailed, which takes the place of the list it could not load.
 */
export const FILE_MESSAGES = {
  /** Case 18. */
  fileTooLarge: "Selected file is too large.\nMaximum file size is 50MB.",
  /** Case 40. */
  filenameTooLong: "Selected file's name is too long.\nMaximum length is 255 characters.",
  /** Case 19 and 20. */
  uploadFailed: "Upload failed. Try to\nupload the file again.",
  /** Case 21. */
  downloadFailed: "There was an error with\ndownloading {filename}.",
  /** Case 22. */
  fileMissing: "File {filename}\nno longer exists.",
  /** Case 23. */
  deleteFailed: "Failed to delete\n{filename}.",
  /** Case 24. */
  listFailed: "Failed to load\nthe files.",
} as const;

/**
 * Notes. The first four belong to the editor, above its title field; the
 * delete failures to the column, below the button that opens it; listFailed
 * to the place of the list it could not load.
 */
export const NOTE_MESSAGES = {
  /** Case 25. */
  noteEmpty: "A note can't be\ncompletely empty.",
  /** Case 26. */
  titleTooLong: "The title is too long.\nTitle limit is 200 characters.",
  /** Case 27. */
  bodyTooLong: "The body is too long.\nBody limit is 2000 characters.",
  /** Case 29. */
  saveFailed: "Failed to save\nthe note.",
  /** Case 28. */
  noteMissing: "Requested note\nno longer exists.",
  /** Case 30. */
  deleteFailed: "Failed to delete\nthe note.",
  /** Case 31. */
  listFailed: "Failed to load\nthe notes.",
} as const;

/**
 * Settings. The failures are shown between the nav buttons on desktop and in
 * the page's top-right corner on mobile; the two removals are dialogs, since
 * they end the session and cannot be left unread.
 */
export const SETTINGS_MESSAGES = {
  /** Case 33. */
  lastPasskeyRemoved: "You removed your last passkey.\nUse email recovery flow to log in again.",
  /** Case 41. */
  sessionPasskeyRemoved: "You removed your current session passkey.\nUse another passkey to log in again.",
  /** Case 41. */
  sessionPasskeyRemovedNarrow: "You removed your current session\npasskey. Use another passkey\nto log in again.",
  /** Case 34. */
  passkeyDeleteFailed: "Failed to delete the passkey.",
  /** Case 35. */
  accountDeleteFailed: "Failed to delete the account.",
  /** Case 36. */
  listFailed: "Failed to list your passkeys.",
} as const;

/** Anywhere: the session ending under the reader. */
export const GLOBAL_MESSAGES = {
  /** Case 39. */
  signedOut: "You have been signed out.\nPlease sign in again.",
} as const;

/** A key of the login page's set, for the field that raises them. */
export type LoginMessage = keyof typeof LOGIN_MESSAGES;
/** A key of the note editor's set. */
export type NoteMessage = keyof typeof NOTE_MESSAGES;
