import { redirect } from "next/navigation";

/**
 * The root route has no UI of its own: it always sends visitors to the
 * login page. This runs on the server (SSR), so the redirect happens
 * before any HTML is sent to the browser.
 */
export default function Home() {
  redirect("/login");
}
