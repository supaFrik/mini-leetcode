import { redirect } from "next/navigation";

/**
 * Trang chủ cũ đã bị bỏ. `/problems` giờ là trang chủ của sản phẩm.
 * Giữ lại một redirect thay vì xoá hẳn để URL cũ không thành 404.
 */
export default function HomePage() {
  redirect("/problems");
}
