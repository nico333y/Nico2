# راه‌اندازی Perplexity برای ادامهٔ پروژهٔ Nico2

این فایل تنظیمات لازم برای اتصال Perplexity به GitHub و ادامهٔ توسعهٔ پروژهٔ `Nico2` را مشخص می‌کند.

## دسترسی‌های موردنیاز

برای ادامهٔ پروژه، توکن GitHub باید حداقل این مجوزها را داشته باشد:

- `Contents: read and write` برای خواندن کد، ساخت فایل، ویرایش فایل و push
- `Pull requests: read and write` برای ساخت، بررسی و به‌روزرسانی Pull Request
- `Issues: read and write` برای مدیریت Issueها
- `Workflows: read and write` فقط در صورت نیاز به اجرا یا اصلاح GitHub Actions

برای امنیت، توکن را Fine-grained بساز و فقط به مخزن `Nico2` دسترسی بده.

## MCP محلی پیشنهادی

برای حل مشکل خواندن متن کامل فایل‌ها، یک MCP محلی با ابزارهای زیر راه‌اندازی شود:

- `read_github_file`: خواندن متن کامل فایل
- `list_github_directory`: فهرست فایل‌ها و پوشه‌ها
- `create_or_update_file`: ساخت یا ویرایش فایل
- `create_branch`: ساخت شاخه
- `push_files`: کامیت چند فایل
- `create_pull_request`: ساخت Pull Request

## پیکربندی نمونه

```json
{
  "mcpServers": {
    "github-nico2": {
      "command": "python",
      "args": ["/path/to/github-read-mcp/server.py"],
      "env": {
        "GITHUB_TOKEN": "<GITHUB_TOKEN>"
      }
    }
  }
}
```

## قواعد کاری Perplexity

1. پیش از هر تغییر مهم، ابتدا فایل‌های مرتبط را بخوان.
2. تغییرات را روی شاخهٔ جداگانه انجام بده و سپس Pull Request بساز.
3. پیش از هر عمل مخرب مانند حذف فایل یا merge، از کاربر تأیید بگیر.
4. توکن و اطلاعات حساس را در کد، README یا Issue قرار نده.
5. ساختار پروژه و فایل‌های Kotlin را پیش از تغییر بررسی کن.

## وضعیت فعلی

- مخزن: `nicooovv89-cyber/Nico2`
- شاخهٔ اصلی: `main`
- زبان اصلی: Kotlin
- دسترسی نوشتن Perplexity: تأیید شده
- مشکل خواندن متن فایل از GitHub connector فعلی: نیازمند MCP محلی یا connector سازگار با resource block
