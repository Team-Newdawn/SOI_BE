# Observability Guide

## 목적

이 구성의 목표는 두 가지입니다.

- 어떤 API가 가장 많이 호출되는지 확인
- 서비스 핵심 행동을 비즈니스 이벤트 단위로 확인

즉, 단순 로그 열람이 아니라 `호출량`, `실패율`, `주요 기능 사용량`을 Grafana에서 바로 볼 수 있게 하는 구성입니다.

## 추가된 구성

- Spring Boot Actuator
- Prometheus metric export
- Prometheus 수집 설정
- Grafana 시각화용 기본 실행 환경
- 비즈니스 이벤트 counter

## 실행 포트

- backend: `8080`
- prometheus: `9090`
- grafana: `3001`

## 메트릭 엔드포인트

백엔드 메트릭:

- `GET /actuator/health`
- `GET /actuator/prometheus`
- `GET /actuator/metrics`

현재 시큐리티 설정에서 위 경로는 인증 없이 접근 가능하다.

## 실행 방법

```bash
docker compose up -d mysql app prometheus grafana
```

접속:

- Prometheus: [http://localhost:9090](http://localhost:9090)
- Grafana: [http://localhost:3001](http://localhost:3001)

기본 Grafana 계정:

- id: `admin`
- password: `admin`

원하면 `.env`에 아래 값을 추가해서 변경 가능:

```env
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me
```

추가로 Grafana는 아래가 자동 provision 된다.

- Prometheus datasource
- `SOI / SOI Observability` 대시보드

즉 첫 실행 후 수동으로 datasource 연결 없이 바로 대시보드를 열 수 있다.

## API 호출량 확인

Spring Boot Actuator가 자동으로 `http.server.requests` 메트릭을 수집한다.
Prometheus에서는 보통 아래 이름으로 보인다.

- `http_server_requests_seconds_count`
- `http_server_requests_seconds_sum`

### 최근 5분 기준 호출률 상위 API

```promql
topk(
  10,
  sum by (uri, method) (
    rate(http_server_requests_seconds_count{
      uri!~"/actuator.*"
    }[5m])
  )
)
```

이 쿼리로 `어떤 API가 현재 가장 많이 호출되는지`를 바로 볼 수 있다.

### 하루 누적 호출 수 상위 API

```promql
topk(
  10,
  sum by (uri, method) (
    increase(http_server_requests_seconds_count{
      uri!~"/actuator.*"
    }[1d])
  )
)
```

포트폴리오에는 이 쿼리가 더 좋다.  
하루 기준으로 `실사용 API TOP 10` 같은 식으로 보여주기 쉽다.

### 4xx/5xx 많은 API 찾기

```promql
topk(
  10,
  sum by (uri, method, status) (
    increase(http_server_requests_seconds_count{
      uri!~"/actuator.*",
      status=~"4..|5.."
    }[1d])
  )
)
```

이걸로 오류가 집중되는 API를 찾을 수 있다.

### 평균 응답시간 상위 API

```promql
topk(
  10,
  (
    sum by (uri, method) (
      rate(http_server_requests_seconds_sum{
        uri!~"/actuator.*"
      }[5m])
    )
  )
  /
  (
    sum by (uri, method) (
      rate(http_server_requests_seconds_count{
        uri!~"/actuator.*"
      }[5m])
    )
  )
)
```

이걸로 느린 API를 찾을 수 있다.

## 비즈니스 이벤트 메트릭

커스텀 counter 이름:

- `soi_business_event_total`

현재 계측되는 이벤트:

- `auth_login_success`
- `auth_refresh_success`
- `auth_logout`
- `post_created`
- `comment_created`
- `notification_created`
- `notification_push`

주요 태그:

- `event`
- `post_type`
- `comment_type`
- `notification_type`
- `platform`
- `status`
- `error_code`

## 비즈니스 이벤트 쿼리 예시

### 하루 기준 주요 기능 사용량

```promql
sum by (event) (
  increase(soi_business_event_total[1d])
)
```

### 게시물 타입별 생성 수

```promql
sum by (post_type) (
  increase(soi_business_event_total{event="post_created"}[1d])
)
```

## 게시물 작성 후 새로고침 지연시간

커스텀 timer 이름:

- `soi.post.refresh.delay`

Prometheus에서는 보통 아래 이름으로 보인다.

- `soi_post_refresh_delay_seconds_count`
- `soi_post_refresh_delay_seconds_sum`
- `soi_post_refresh_delay_seconds_bucket`

측정 방식:

- `POST /post/create` 성공 후 사용자별 최근 게시물 생성 시각을 서버 메모리에 저장한다.
- 같은 사용자가 아래 게시물 목록 조회 API 중 하나를 처음 호출하면, 생성 시각부터 조회 시각까지의 지연시간을 기록한다.
- 한 게시물 생성 이벤트당 첫 조회만 측정한다.
- `userId`는 Prometheus 태그에 넣지 않는다. 사용자별 고카디널리티 태그는 Prometheus 성능과 저장 비용에 좋지 않다.

측정되는 조회 경로:

- `GET /post/find-all`: `source="feed"`
- `GET /post/find-by/category`: `source="category"`
- `GET /post/find/by-user-id`: `source="user_media"`

주요 태그:

- `source`
- `post_type`

### 평균 새로고침 지연시간

```promql
sum(rate(soi_post_refresh_delay_seconds_sum[1d]))
/
sum(rate(soi_post_refresh_delay_seconds_count[1d]))
```

### 조회 경로별 평균 새로고침 지연시간

```promql
sum by (source) (
  rate(soi_post_refresh_delay_seconds_sum[1d])
)
/
sum by (source) (
  rate(soi_post_refresh_delay_seconds_count[1d])
)
```

### 5초 이내 새로고침 비율

```promql
sum(increase(soi_post_refresh_delay_seconds_bucket{le="5.0"}[1d]))
/
sum(increase(soi_post_refresh_delay_seconds_count[1d]))
```

### 10초 이내 새로고침 비율

```promql
sum(increase(soi_post_refresh_delay_seconds_bucket{le="10.0"}[1d]))
/
sum(increase(soi_post_refresh_delay_seconds_count[1d]))
```

### p50/p95 새로고침 지연시간

```promql
histogram_quantile(
  0.5,
  sum(rate(soi_post_refresh_delay_seconds_bucket[1d])) by (le)
)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(soi_post_refresh_delay_seconds_bucket[1d])) by (le)
)
```

### 댓글 타입별 생성 수

```promql
sum by (comment_type) (
  increase(soi_business_event_total{event="comment_created"}[1d])
)
```

### 알림 타입별 생성 수

```promql
sum by (notification_type) (
  increase(soi_business_event_total{event="notification_created"}[1d])
)
```

### 플랫폼별 푸시 성공 수

```promql
sum by (platform) (
  increase(soi_business_event_total{
    event="notification_push",
    status="success"
  }[1d])
)
```

### 플랫폼별 푸시 실패 수

```promql
sum by (platform, error_code) (
  increase(soi_business_event_total{
    event="notification_push",
    status="failure"
  }[1d])
)
```

### 플랫폼별 푸시 성공률

```promql
sum by (platform) (
  increase(soi_business_event_total{
    event="notification_push",
    status="success"
  }[1d])
)
/
sum by (platform) (
  increase(soi_business_event_total{
    event="notification_push"
  }[1d])
)
```

## Grafana 패널 추천

포트폴리오용으로는 아래 패널 6개면 충분하다.

- 최근 5분 호출률 상위 API Top 10
- 하루 누적 호출 수 상위 API Top 10
- 4xx/5xx 많은 API Top 10
- 평균 응답시간 상위 API Top 10
- 하루 기준 주요 기능 사용량
- iOS/Android 푸시 성공률 비교

현재 기본 provision 대시보드에도 이 항목들이 포함되어 있다.

## 포트폴리오에서 보여주기 좋은 해석 예시

- `GET /post/find/by-user-id` 호출량이 가장 높았다
- `POST /auth/refresh` 호출량으로 access token 만료 패턴을 추적했다
- `notification_push` 실패가 iOS에서 더 자주 발생하는지 비교했다
- `comment_created` 대비 `post_created` 비율로 사용자 참여도를 해석했다
- 4xx가 많은 API를 기준으로 프론트 요청 실수 구간을 찾았다

## 주의사항

- `uri` 태그는 actuator가 수집한 라우트 기준으로 보되, actuator 자체 경로는 제외해서 보는 것이 좋다
- Prometheus/Grafana를 운영에 그대로 외부 공개하면 안 된다
- access token, refresh token, phone number 같은 민감값은 메트릭 태그에 넣지 않는다
- 사용자 식별값을 태그로 넣으면 cardinality가 폭증하므로 금지하는 것이 좋다
