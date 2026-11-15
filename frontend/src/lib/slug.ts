/** Khớp ràng buộc slug của backend (UpsertPlanRequest, UpsertTopicRequest). */
const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export const isSlug = (value: string) => SLUG_RE.test(value);

/** Tiêu đề → slug: bỏ dấu tiếng Việt, chữ thường, gộp ký tự khác thành "-". */
export const slugify = (title: string) =>
  title.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/gi, "d").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 200);
