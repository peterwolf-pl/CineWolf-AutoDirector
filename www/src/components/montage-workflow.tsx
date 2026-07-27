import { ArrowRightIcon } from "@/components/icons";

export type MontageStep = {
  title: string;
  description?: string;
};

export function MontageWorkflow({
  steps,
  compact = false,
}: {
  steps: MontageStep[];
  compact?: boolean;
}) {
  return (
    <ol className={`montage-workflow ${compact ? "montage-workflow-compact" : ""}`}>
      {steps.map((step, index) => (
        <li key={step.title}>
          <span aria-hidden="true" className="workflow-step-number">
            {String(index + 1).padStart(2, "0")}
          </span>
          <div>
            <h3>{step.title}</h3>
            {step.description ? <p>{step.description}</p> : null}
          </div>
          {index < steps.length - 1 ? <ArrowRightIcon className="workflow-arrow" size={17} /> : null}
        </li>
      ))}
    </ol>
  );
}

export function MontageProcessDiagram() {
  const process = [
    "Replay Range",
    "Event Analysis",
    "Scene Detection",
    "Montage Plan",
    "Shot Preview",
    "Flashback Keyframes",
  ];

  return (
    <div aria-label="Replay Range to Flashback Keyframes process" className="montage-process" role="img">
      {process.map((item, index) => (
        <div key={item}>
          <span>{item}</span>
          {index < process.length - 1 ? <ArrowRightIcon aria-hidden="true" size={17} /> : null}
        </div>
      ))}
    </div>
  );
}
