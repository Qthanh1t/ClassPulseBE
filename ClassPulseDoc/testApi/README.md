# ClassPulse — API Test Guide (M09–M11)

## Công cụ

Các file `.http` trong thư mục này dùng được trực tiếp với:
- **IntelliJ IDEA** — mở file, nhấn ▶ cạnh từng request
- **VS Code** — cài extension [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client), nhấn `Send Request`
- **curl** — copy từng block ra terminal

## Thứ tự test

Các module phụ thuộc nhau theo đúng thứ tự sau:

```
1. Auth (đăng ký + đăng nhập teacher + 2 student)
2. Classroom (tạo lớp + student join lớp)
3. M09 — Session  (teacher start → student join → presence)
4. M10 — Question (teacher create → start → end → stats)
5. M11 — Answer   (student submit → teacher/student get)
```

Luôn chạy theo đúng thứ tự này trong một phiên test mới vì mỗi bước cần ID từ bước trước.

## File trong thư mục này

| File | Nội dung |
|------|---------|
| `00_prereq.http` | Đăng ký + đăng nhập, tạo lớp, join lớp — cần chạy trước |
| `M09_session.http` | Start, join, presence, leave, end session |
| `M10_question.http` | Tạo, start, end câu hỏi; xem stats |
| `M11_answer.http` | Student nộp bài; teacher/student xem đáp án |

## Biến môi trường (IntelliJ)

Tạo file `http-client.env.json` tại root project với nội dung:

```json
{
  "dev": {
    "baseUrl": "http://localhost:8080",
    "teacherToken": "",
    "student1Token": "",
    "student2Token": "",
    "classroomId": "",
    "sessionId": "",
    "questionId": ""
  }
}
```

Sau mỗi bước, copy token/ID từ response vào file env để các request tiếp theo dùng được.

## Lưu ý nhanh

- App chạy ở `http://localhost:8080`
- Tất cả response bọc trong `{ "success": true, "data": ... }`
- Access token có TTL **15 phút** — login lại nếu nhận 401
- Session phải `status: active` thì mới start được question
- Question phải `status: running` thì student mới submit được
