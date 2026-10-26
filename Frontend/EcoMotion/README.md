# React + Vite

## Kiểm thử EcoMotion

```bash
npm test
npm run lint
npm run build
npm run test:e2e
```

`test:e2e` khởi động Vite và chạy Playwright trên Google Chrome. Các kịch bản trong
`e2e/` kiểm tra UI renter/provider cho cảnh báo xe về trễ và thao tác đổi xe;
API được chặn ở biên HTTP để bài test ổn định và không sửa dữ liệu phát triển.

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.
