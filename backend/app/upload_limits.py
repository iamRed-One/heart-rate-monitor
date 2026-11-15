"""Bound the upload request before multipart parsing, including chunked bodies."""

from fastapi.responses import JSONResponse

from app.parsers.csv import MAX_FILE_BYTES

# File limit plus bounded multipart metadata/header overhead.
MAX_REQUEST_BYTES = MAX_FILE_BYTES + 64 * 1024


class UploadLimitMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["path"] != "/api/imports/preview":
            return await self.app(scope, receive, send)
        headers = dict(scope.get("headers", []))
        declared = headers.get(b"content-length")
        if declared is not None:
            try:
                if int(declared) < 0:
                    raise ValueError
                oversized = int(declared) > MAX_REQUEST_BYTES
            except ValueError:
                return await JSONResponse(
                    status_code=400, content={"code": "invalid_length"}
                )(scope, receive, send)
            if oversized:
                return await self.reject(scope, receive, send)
        body_buffer, total = bytearray(), 0
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            chunk = message.get("body", b"")
            total += len(chunk)
            if total > MAX_REQUEST_BYTES:
                return await self.reject(scope, receive, send)
            body_buffer.extend(chunk)
            if not message.get("more_body", False):
                break
        body = bytes(body_buffer)
        delivered = False

        async def bounded_receive():
            nonlocal delivered
            if delivered:
                return await receive()
            delivered = True
            return {"type": "http.request", "body": body, "more_body": False}

        return await self.app(scope, bounded_receive, send)

    async def reject(self, scope, receive, send):
        return await JSONResponse(
            status_code=413,
            content={
                "code": "upload_too_large",
                "message": "Upload exceeds the request limit.",
            },
        )(scope, receive, send)
