import type { Metadata } from "next";

import { FaqAccordion } from "@/components/faq-accordion";
import { BreadcrumbJsonLd, JsonLd } from "@/components/json-ld";
import { CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { faq } from "@/data/faq";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Frequently Asked Questions",
  description:
    "Answers about CineWolf AutoDirector’s local replay processing, Flashback support, editable camera paths, collision handling, vehicle profiles, montage presets, and compatibility.",
  path: "/faq",
});

export default function FaqPage() {
  return (
    <div className="page">
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@type": "FAQPage",
          mainEntity: faq.map((item) => ({
            "@type": "Question",
            name: item.question,
            acceptedAnswer: { "@type": "Answer", text: item.answer },
          })),
        }}
      />
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "FAQ", path: "/faq" }]} />
      <PageHero
        description="Straight answers about the camera tool, local replay processing, available shots, compatibility, editable output, and the bounds CineWolf intentionally keeps visible."
        title="Questions from the edit room"
      />
      <section className="site-section">
        <SectionHeading
          description="The FAQ avoids compatibility promises that are not verified in the current release. If you are preparing an install, check the Flashback page and download requirements as well."
          label="FAQ"
          title="Clear answers, clear boundaries"
        />
        <FaqAccordion items={faq} />
      </section>
      <CtaSection
        description="If you are ready to test a replay, verify the installation stack first and start with one previewed shot."
        title="Move from question to first shot"
      />
    </div>
  );
}
