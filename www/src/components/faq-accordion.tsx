"use client";

import { useId, useState } from "react";

import { ChevronDownIcon } from "@/components/icons";

type FaqItem = {
  question: string;
  answer: string;
};

export function FaqAccordion({ items }: { items: FaqItem[] }) {
  const [openIndex, setOpenIndex] = useState<number | null>(0);
  const baseId = useId();

  return (
    <div className="faq-accordion">
      {items.map((item, index) => {
        const isOpen = openIndex === index;
        const panelId = `${baseId}-panel-${index}`;
        const buttonId = `${baseId}-button-${index}`;

        return (
          <article className={`faq-item ${isOpen ? "faq-item-open" : ""}`} key={item.question}>
            <h3>
              <button
                aria-controls={panelId}
                aria-expanded={isOpen}
                id={buttonId}
                onClick={() => setOpenIndex(isOpen ? null : index)}
                type="button"
              >
                <span>{item.question}</span>
                <ChevronDownIcon size={19} />
              </button>
            </h3>
            <div
              aria-labelledby={buttonId}
              className="faq-answer"
              hidden={!isOpen}
              id={panelId}
              role="region"
            >
              <p>{item.answer}</p>
            </div>
          </article>
        );
      })}
    </div>
  );
}
