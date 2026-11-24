import zones from "../../shared/campus/segamat-colleges.json";

export function CollegeDirectory() {
  return (
    <details lang="ms" className="rounded-xl border border-line bg-paper">
      <summary className="cursor-pointer px-4 py-3 font-bold">
        Senarai kolej kediaman UiTM Segamat
      </summary>
      <div className="border-t border-line p-4">
        <p className="text-sm text-muted">UiTM Cawangan Johor, Kampus Segamat</p>
        <div className="mt-4 grid gap-5 [grid-template-columns:repeat(auto-fit,minmax(min(100%,14rem),1fr))]">
          {zones.map(({ zone, residents, colleges }) => (
            <section key={zone}>
              <h3 className="font-display text-lg font-bold">{zone}</h3>
              <p className="text-sm text-muted">{residents}</p>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm">
                {colleges.map((college) => <li key={college}>{college}</li>)}
              </ul>
            </section>
          ))}
        </div>
      </div>
    </details>
  );
}
