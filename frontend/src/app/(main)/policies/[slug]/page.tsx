import { PolicyView } from "@/components/policies/policy-view";

export default function PolicyPage({ params }: { params: { slug: string } }) {
  return <PolicyView slug={params.slug} />;
}
