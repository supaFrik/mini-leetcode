/** Lộ trình học (theo docs/mockups/StudyPlan.dc.html). Bài trỏ theo slug; slug không có trong API thì trang bỏ qua. */
export interface StudyPlan {
  id: string;
  name: string;
  desc: string;
  modules: { name: string; slugs: string[] }[];
}

export const STUDY_PLANS: StudyPlan[] = [
  {
    id: "dsa",
    name: "DSA Foundations",
    desc: "Core data structures first, then graphs and dynamic programming.",
    modules: [
      { name: "1. Arrays and hashing", slugs: ["two-sum", "binary-search", "top-k-frequent-elements"] },
      { name: "2. Stacks and linked lists", slugs: ["valid-parentheses", "reverse-linked-list", "merge-k-sorted-lists"] },
      { name: "3. Graphs and dynamic programming", slugs: ["number-of-islands", "course-schedule", "coin-change"] },
    ],
  },
  {
    id: "oop",
    name: "OOP & Socket",
    desc: "Model real systems with classes, then connect them over the network.",
    modules: [
      { name: "1. Classes and encapsulation", slugs: ["bank-account-class"] },
      { name: "2. Inheritance and polymorphism", slugs: ["library-management-system"] },
      { name: "3. Sockets and threads", slugs: ["multi-client-echo-server", "tcp-chat-room"] },
    ],
  },
];
