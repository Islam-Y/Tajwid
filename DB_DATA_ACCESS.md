# Как выгружать данные из БД простыми шагами

Документ для первого запуска.  
Здесь есть 2 способа:

1. Через API приложения (`curl`/Postman) - это самый простой вариант, его обычно достаточно.
2. Напрямую из PostgreSQL - это опционально, если нужен полный SQL-доступ.

## 1) Быстрый старт: что выбрать

Если вы просто хотите получить данные, выбирайте способ 1 (API).  
Там не нужны SSH-туннели и подключение к самой БД.

## 2) Способ 1 (рекомендуется): получить данные через API

### 2.1 Что нужно заранее

- Адрес приложения: `https://bot.tartilschool.online`
- Логин и пароль администратора API
- Ваш IP должен быть разрешен в серверной настройке `TAJWID_ADMIN_SECURITY_ALLOWED_IPS`

Если не уверены, есть ли у вас доступ, проверьте:

(копируйте команды как есть в командную строку)
```bash
curl -i -u "ВАШ_ЛОГИН:ВАШ_ПАРОЛЬ" \
  "https://bot.tartilschool.online/api/admin/export/snapshot"
```

- `200` - доступ есть.
- `401` - неправильный логин/пароль.
- `403` - ваш IP не в разрешенном списке.

### 2.2 Выгрузка через curl

Сначала один раз задайте переменные:

```bash
export BASE_URL="https://bot.tartilschool.online"
export ADMIN_USER="ВАШ_ЛОГИН"
export ADMIN_PASS="ВАШ_ПАРОЛЬ"
```

Полный снимок в JSON:

```bash
curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/export/snapshot" -o snapshot.json
```

CSV-файлы с основными таблицами:

```bash
curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/export/users.csv" -o users.csv

curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/export/flow-contexts.csv" -o flow-contexts.csv

curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/export/user-tags.csv" -o user-tags.csv

curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/export/referral-link-usage.csv" -o referral-link-usage.csv
```

Данные по детям (админские ручки):

```bash
curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/children/users?limit=100" -o children-users.json

curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  "$BASE_URL/api/admin/children/users/328048006" -o child-by-user.json

curl -fsS -u "$ADMIN_USER:$ADMIN_PASS" \
  -H "Content-Type: application/json" \
  -d '{"onlyWithChildren":true,"childrenStudyQuran":true,"limit":100}' \
  "$BASE_URL/api/admin/children/search" -o children-search.json
```

### 2.3 То же самое через Postman (без команд)

1. Откройте Postman.
2. Создайте `Environment`, например `tajwid-prod`.
3. Добавьте переменные:
- `base_url` = `https://bot.tartilschool.online`
- `admin_user` = ваш логин
- `admin_pass` = ваш пароль
- `user_id` = Telegram userId (если нужен запрос по конкретному пользователю)
- `phone` = номер телефона в цифрах (если нужен public-запрос)
4. Создайте новую папку `Admin`.
5. Для каждого админ-запроса:
- Method: `GET` или `POST`
- URL: например `{{base_url}}/api/admin/export/snapshot`
- Вкладка `Authorization` -> `Basic Auth`
- Username: `{{admin_user}}`
- Password: `{{admin_pass}}`
6. Для `POST {{base_url}}/api/admin/children/search`:
- `Body` -> `raw` -> `JSON`
- Вставьте:

```json
{
  "onlyWithChildren": true,
  "childrenStudyQuran": true,
  "limit": 100
}
```

Готовые URL для Postman:

- `GET {{base_url}}/api/admin/export/snapshot`
- `GET {{base_url}}/api/admin/export/users.csv`
- `GET {{base_url}}/api/admin/export/flow-contexts.csv`
- `GET {{base_url}}/api/admin/export/user-tags.csv`
- `GET {{base_url}}/api/admin/export/referral-link-usage.csv`
- `GET {{base_url}}/api/admin/children/users?limit=100`
- `GET {{base_url}}/api/admin/children/users/{{user_id}}`
- `POST {{base_url}}/api/admin/children/search`

### 2.4 Public-ручки (для обычного пользователя)

Эти ручки работают по паре `userId + phone`.

Через `curl`:

```bash
curl -fsS \
  "$BASE_URL/api/public/children/stats?userId=328048006&phone=79172343480"

curl -fsS -H "Content-Type: application/json" \
  -d '{"userId":328048006,"phone":"79172343480"}' \
  "$BASE_URL/api/public/children/self"
```

Через Postman:

- `GET {{base_url}}/api/public/children/stats?userId={{user_id}}&phone={{phone}}`
- `POST {{base_url}}/api/public/children/self`
- Body JSON:

```json
{
  "userId": {{user_id}},
  "phone": "{{phone}}"
}
```

## 3) Способ 2 (опционально): прямой доступ к PostgreSQL

Используйте этот способ только если нужен SQL.  
Если нужна обычная выгрузка, этот блок можно пропустить.

### 3.1 Что такое туннель простыми словами

Туннель - это временный безопасный "мост" с вашего ноутбука к БД на сервере.  
Пока мост открыт, можно подключаться к БД с вашего компьютера.

### 3.2 Открыть туннель

```bash
ssh -N -L 5433:127.0.0.1:5432 \
  -i ~/.ssh/id_ed25519_tajwid_deploy \
  root@80.66.89.226
```

Важно:

- Не закрывайте это окно терминала, пока работаете с БД.
- После команды БД будет доступна на вашем ноутбуке: `127.0.0.1:5433`.

### 3.3 Проверить логин/пароль БД

На сервере:

```bash
ssh -i ~/.ssh/id_ed25519_tajwid_deploy root@80.66.89.226 \
  "grep -E '^POSTGRES_(DB|USER|PASSWORD)=' /opt/tajwid/.env"
```

### 3.4 Подключиться к БД через `psql`

```bash
PGPASSWORD="ПАРОЛЬ_ИЗ_ENV" psql \
  -h 127.0.0.1 -p 5433 \
  -U tajwid -d tajwid
```

Примеры SQL:

```sql
SELECT * FROM users;
SELECT * FROM flow_contexts;
SELECT * FROM user_tags;
SELECT * FROM referral_link_usage;
```

Экспорт таблиц в CSV:

```sql
\copy users TO 'users.csv' CSV HEADER
\copy flow_contexts TO 'flow_contexts.csv' CSV HEADER
\copy user_tags TO 'user_tags.csv' CSV HEADER
\copy referral_link_usage TO 'referral-link-usage.csv' CSV HEADER
```

## 4) Частые ошибки и как исправить

- `401 Unauthorized` на `/api/admin/*`: проверьте логин и пароль.
- `403 Forbidden` на `/api/admin/*`: ваш IP не разрешен на сервере.
- `404` на `/api/public/children/*`: неверная пара `userId + phone`.
- `Connection refused` при подключении к БД на `127.0.0.1:5433`: туннель не открыт или закрыт.
