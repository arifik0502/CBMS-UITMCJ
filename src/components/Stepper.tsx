export function Stepper({
  value, min = 0, max, onChange, label
}: { value: number; min?: number; max: number; onChange: (next: number) => void; label: string }) {
  return (
    <div className="flex items-center gap-2" role="group" aria-label={label}>
      <button type="button" aria-label={`Decrease ${label}`} disabled={value <= min}
        onClick={() => onChange(value - 1)}
        className="grid size-11 place-items-center rounded-full border border-muted bg-paper text-xl font-bold disabled:opacity-40">−</button>
      <output aria-live="polite" className="w-8 text-center font-display text-xl font-extrabold">{value}</output>
      <button type="button" aria-label={`Increase ${label}`} disabled={value >= max}
        onClick={() => onChange(value + 1)}
        className="grid size-11 place-items-center rounded-full border border-muted bg-paper text-xl font-bold disabled:opacity-40">+</button>
    </div>
  );
}
