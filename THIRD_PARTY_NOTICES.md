# Tài nguyên giao diện bên thứ ba

Các tài nguyên dưới đây được phục vụ trực tiếp từ ứng dụng. Trình duyệt không tải CDN, npm hoặc Google Fonts khi chạy.

| Tài nguyên | Nguồn | Giấy phép trong source |
| --- | --- | --- |
| Lucide SVG, phiên bản 0.468.0 | [lucide-static trên npm](https://www.npmjs.com/package/lucide-static/v/0.468.0), [Lucide](https://github.com/lucide-icons/lucide) | [ISC và thông báo Feather](src/main/resources/static/icons/LICENSE-lucide.txt) |
| Be Vietnam Pro Regular/Bold | [Google Fonts repository](https://github.com/google/fonts/tree/main/ofl/bevietnampro), [tác giả](https://github.com/bettergui/BeVietnamPro) | [SIL Open Font License 1.1](src/main/resources/static/fonts/OFL.txt) |

Lucide được trích thành SVG symbol sprite trong `static/icons/lucide.svg`; font TTF giữ nguyên. `tank.svg` và `battle-scene.svg` là hình SVG tự viết cho giao diện dự án. Không có bước tải các tài nguyên này trong Gradle hoặc Docker build.
