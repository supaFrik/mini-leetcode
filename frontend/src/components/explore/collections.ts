/** Danh sách bài chọn sẵn (theo docs/mockups/Explore.dc.html). Bài trỏ theo slug; số bài đếm từ những slug API trả về. */
export interface Collection {
  id: string;
  track: string;
  name: string;
  desc: string;
  slugs: string[];
}

export const COLLECTIONS: Collection[] = [
  {
    id: "start-here",
    track: "DSA",
    name: "Start here",
    desc: "Short problems that cover arrays, strings and hash maps.",
    slugs: [
      "two-sum",
      "valid-parentheses",
      "binary-search",
      "reverse-linked-list",
      "top-k-frequent-elements",
      "longest-substring-without-repeating-characters",
    ],
  },
  {
    id: "graphs",
    track: "DSA",
    name: "Graphs in practice",
    desc: "From grid traversal to topological sort.",
    slugs: ["number-of-islands", "course-schedule", "word-ladder"],
  },
  {
    id: "socket-lab",
    track: "OOP",
    name: "Socket lab",
    desc: "Build servers and clients that talk over TCP.",
    slugs: ["multi-client-echo-server", "file-transfer-over-tcp", "tcp-chat-room"],
  },
];
