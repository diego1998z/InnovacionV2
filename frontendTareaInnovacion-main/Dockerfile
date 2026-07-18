FROM nginx:1.27-alpine

COPY . /usr/share/nginx/html

EXPOSE 80
CMD ["/bin/sh", "-c", "printf 'window.CREDIT_AI_API_BASE = \"%s\";\n' \"${API_BASE_URL:-http://localhost:8080/api}\" > /usr/share/nginx/html/config.js && exec nginx -g 'daemon off;'"]
