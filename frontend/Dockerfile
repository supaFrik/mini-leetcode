FROM node:20-alpine AS deps
RUN apk add --no-cache libc6-compat
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG NEXT_PUBLIC_USE_MOCK=false
ARG NEXT_PUBLIC_ENABLE_RUN=false
ARG NEXT_PUBLIC_ENABLE_DRAFT_SYNC=false
ENV NEXT_PUBLIC_USE_MOCK=$NEXT_PUBLIC_USE_MOCK
ENV NEXT_PUBLIC_ENABLE_RUN=$NEXT_PUBLIC_ENABLE_RUN
ENV NEXT_PUBLIC_ENABLE_DRAFT_SYNC=$NEXT_PUBLIC_ENABLE_DRAFT_SYNC

RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder --chown=nextjs:nodejs /app/.next ./.next
COPY --from=builder /app/next.config.mjs ./

USER nextjs

EXPOSE 3000

CMD ["npm", "start"]
