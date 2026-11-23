import { redirect } from "next/navigation";

/** Lesson library đã gộp vào trang Study plans. */
export default function AdminTopicsPage() {
  redirect("/admin/study-plans?tab=lessons");
}
