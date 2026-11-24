import { useEffect, useState } from "react";

export function HomeScreenGuide() {
  const [installed, setInstalled] = useState(() =>
    window.matchMedia("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );

  useEffect(() => {
    const onInstall = () => setInstalled(true);
    window.addEventListener("appinstalled", onInstall);
    return () => window.removeEventListener("appinstalled", onInstall);
  }, []);

  if (installed) return null;
  return (
    <details className="install-guide">
      <summary>Add to your phone’s Home Screen</summary>
      <div className="px-4 pb-5 sm:px-5">
        <p>Save this shop on your phone. Next time, tap its icon to open the menu.</p>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <h3>iPhone or iPad</h3>
            <ol>
              <li>Open this website in <strong>Safari</strong>.</li>
              <li>Tap <strong>Share</strong> (the square with an arrow pointing up). If it’s hidden, tap the <strong>More (…)</strong> button first.</li>
              <li>Scroll down and tap <strong>Add to Home Screen</strong>. If you see <strong>Open as Web App</strong>, leave it on. Tap <strong>Add</strong>.</li>
            </ol>
          </div>
          <div>
            <h3>Android phone</h3>
            <ol>
              <li>Open this website in <strong>Chrome</strong>.</li>
              <li>Tap the <strong>three dots (⋮)</strong> at the top right.</li>
              <li>Tap <strong>Add to Home screen</strong> or <strong>Install app</strong>, then follow the message to finish.</li>
            </ol>
          </div>
        </div>
      </div>
    </details>
  );
}
