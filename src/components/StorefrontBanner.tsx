import { useEffect, useRef, useState, type ReactNode } from "react";

const reducedMotion = "(prefers-reduced-motion: reduce)";

export function StorefrontBanner({ hero }: { hero?: ReactNode }) {
  const slideCount = hero ? 3 : 2;
  const [active, setActive] = useState(0);
  const [playing, setPlaying] = useState(() => !window.matchMedia(reducedMotion).matches);
  const [hovered, setHovered] = useState(false);
  const start = useRef<{ x: number; y: number } | null>(null);
  const position = active % slideCount;

  useEffect(() => {
    const preference = window.matchMedia(reducedMotion);
    const stop = () => { if (preference.matches) setPlaying(false); };
    preference.addEventListener("change", stop);
    return () => preference.removeEventListener("change", stop);
  }, []);

  useEffect(() => {
    if (!playing || hovered) return;
    const timer = window.setInterval(() => {
      if (!document.hidden) setActive((current) => (current + 1) % slideCount);
    }, 8000);
    return () => window.clearInterval(timer);
  }, [playing, hovered, slideCount]);

  function move(direction: number) {
    setPlaying(false);
    setActive((current) => (current + direction + slideCount) % slideCount);
  }

  const slides = [
    ...(hero ? [hero] : []),
    <div className="storefront-hero snack-banner" lang="ms">
      <div className="hero-copy">
        <h2>Snack Malam</h2>
        <p>Ayam Gunting, Ayam Sate Gunting dan Sosej Gunting.</p>
      </div>
      <img src="/snack-malam.webp" className="hero-image" width="1040" height="780" alt="" />
    </div>,
    <div className="teaser-banner">
      <h2>New menu<br /><em>coming soon</em></h2>
    </div>
  ];

  return (
    <section aria-label="Storefront highlights" aria-roledescription="carousel"
      onFocusCapture={(event) => { if (event.target.matches(":focus-visible")) setPlaying(false); }}
      onPointerEnter={(event) => { if (event.pointerType === "mouse") setHovered(true); }}
      onPointerLeave={() => setHovered(false)}>
      <div className="banner-window"
        onTouchStart={(event) => {
          const touch = event.touches[0];
          start.current = event.touches.length === 1 ? { x: touch.clientX, y: touch.clientY } : null;
        }}
        onTouchCancel={() => { start.current = null; }}
        onTouchEnd={(event) => {
          if (!start.current) return;
          const dx = event.changedTouches[0].clientX - start.current.x;
          const dy = event.changedTouches[0].clientY - start.current.y;
          start.current = null;
          if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.5) move(dx < 0 ? 1 : -1);
        }}>
        <div className="banner-track" style={{ transform: `translateX(-${position * 100}%)` }}>
          {slides.map((slide, index) => (
            <div key={index} role="group" aria-roledescription="slide" aria-label={`${index + 1} of ${slideCount}`}
              inert={index !== position} aria-hidden={index !== position} className="banner-slide">
              {slide}
            </div>
          ))}
        </div>
      </div>
      <div className="banner-controls">
        <button type="button" onClick={() => setPlaying(!playing)}>{playing ? "Pause slides" : "Play slides"}</button>
        <div className="flex items-center gap-2">
          <button type="button" aria-label="Previous slide" onClick={() => move(-1)}>←</button>
          <span aria-live={playing ? "off" : "polite"} aria-atomic="true" className="tabular-nums">{position + 1} / {slideCount}</span>
          <button type="button" aria-label="Next slide" onClick={() => move(1)}>→</button>
        </div>
      </div>
    </section>
  );
}
