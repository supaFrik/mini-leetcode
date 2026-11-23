-- V26: màu riêng cho từng tag; NULL = dùng màu của category.
ALTER TABLE tags ADD COLUMN color VARCHAR(7) NULL;
