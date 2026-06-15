<?php

$config = require __DIR__ . "/config.php";

function send_json($statusCode, $payload) {
    http_response_code($statusCode);
    header("Content-Type: application/json; charset=utf-8");
    header("Cache-Control: no-store");
    echo json_encode($payload);
    exit;
}

function send_text($statusCode, $body) {
    http_response_code($statusCode);
    header("Content-Type: text/plain; charset=utf-8");
    header("Cache-Control: no-store");
    echo $body;
    exit;
}

function request_origin() {
    $isHttps = (!empty($_SERVER["HTTPS"]) && $_SERVER["HTTPS"] !== "off") ||
        (isset($_SERVER["HTTP_X_FORWARDED_PROTO"]) && $_SERVER["HTTP_X_FORWARDED_PROTO"] === "https");
    $scheme = $isHttps ? "https" : "http";
    $host = $_SERVER["HTTP_HOST"] ?? "localhost";
    return $scheme . "://" . $host;
}

function raw_request_body() {
    $body = file_get_contents("php://input");
    return $body === false ? "" : $body;
}

function extract_auth_cookies($cookieHeader) {
    $parts = array_map("trim", explode(";", (string) $cookieHeader));
    $allowed = [];
    foreach ($parts as $part) {
        if (
            strpos($part, "better-auth.session_token=") === 0 ||
            strpos($part, "better-auth.session_data=") === 0 ||
            strpos($part, "__Secure-better-auth.session_token=") === 0 ||
            strpos($part, "__Secure-better-auth.session_data=") === 0 ||
            strpos($part, "__Host-better-auth.session_token=") === 0 ||
            strpos($part, "__Host-better-auth.session_data=") === 0
        ) {
            $allowed[] = $part;
        }
    }
    return implode("; ", $allowed);
}

function rewrite_set_cookie_for_current_host($cookie) {
    $segments = array_filter(array_map("trim", explode(";", $cookie)), "strlen");
    $filtered = [];
    foreach ($segments as $segment) {
        if (stripos($segment, "Domain=") === 0) {
            continue;
        }
        $filtered[] = $segment;
    }
    $hasPath = false;
    foreach ($filtered as $segment) {
        if (stripos($segment, "Path=") === 0) {
            $hasPath = true;
            break;
        }
    }
    if (!$hasPath) {
        $filtered[] = "Path=/";
    }
    return implode("; ", $filtered);
}

function proxy_request($url, $method, $headers, $body = null) {
    $curl = curl_init($url);
    $responseHeaders = [];
    curl_setopt($curl, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($curl, CURLOPT_CUSTOMREQUEST, $method);
    curl_setopt($curl, CURLOPT_HTTPHEADER, $headers);
    curl_setopt($curl, CURLOPT_HEADERFUNCTION, function ($curlHandle, $headerLine) use (&$responseHeaders) {
        $trimmed = trim($headerLine);
        $length = strlen($headerLine);
        if ($trimmed === "" || strpos($trimmed, ":") === false) {
            return $length;
        }
        [$name, $value] = explode(":", $trimmed, 2);
        $normalized = strtolower(trim($name));
        $responseHeaders[$normalized][] = trim($value);
        return $length;
    });
    if ($body !== null && !in_array($method, ["GET", "HEAD"], true)) {
        curl_setopt($curl, CURLOPT_POSTFIELDS, $body);
    }
    $responseBody = curl_exec($curl);
    if ($responseBody === false) {
        $error = curl_error($curl);
        curl_close($curl);
        throw new RuntimeException($error ?: "Unknown cURL error");
    }
    $statusCode = curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    curl_close($curl);
    return [
        "status" => $statusCode,
        "body" => $responseBody,
        "headers" => $responseHeaders
    ];
}

function upstream_headers($config, $includeCookies = true) {
    $headers = [
        "Content-Type: application/json",
        "X-Database-Instance: " . $config["NCB_INSTANCE"],
        "Authorization: Bearer " . $config["NCB_SECRET_KEY"],
        "Origin: " . request_origin()
    ];
    if ($includeCookies) {
        $cookies = extract_auth_cookies($_SERVER["HTTP_COOKIE"] ?? "");
        if ($cookies !== "") {
            $headers[] = "Cookie: " . $cookies;
        }
    }
    return $headers;
}

function decoded_json_or_null($text) {
    if ($text === null || $text === "") {
        return null;
    }
    $decoded = json_decode($text, true);
    return json_last_error() === JSON_ERROR_NONE ? $decoded : null;
}

function append_query($baseUrl, $queryString) {
    if ($queryString === "") {
        return $baseUrl;
    }
    return $baseUrl . "&" . $queryString;
}

function get_session_user($config) {
    $cookies = extract_auth_cookies($_SERVER["HTTP_COOKIE"] ?? "");
    if ($cookies === "") {
        return null;
    }
    $url = $config["NCB_AUTH_API_URL"] . "/get-session?instance=" . rawurlencode($config["NCB_INSTANCE"]);
    $result = proxy_request($url, "GET", [
        "Content-Type: application/json",
        "X-Database-Instance: " . $config["NCB_INSTANCE"],
        "Authorization: Bearer " . $config["NCB_SECRET_KEY"],
        "Cookie: " . $cookies
    ]);
    if ($result["status"] < 200 || $result["status"] >= 300) {
        return null;
    }
    $data = decoded_json_or_null($result["body"]);
    return is_array($data) && isset($data["user"]) ? $data["user"] : null;
}

function forward_response($result) {
    http_response_code($result["status"]);
    $contentType = $result["headers"]["content-type"][0] ?? "application/json; charset=utf-8";
    header("Content-Type: " . $contentType);
    header("Cache-Control: no-store");
    if (!empty($result["headers"]["set-cookie"])) {
        foreach ($result["headers"]["set-cookie"] as $cookie) {
            header("Set-Cookie: " . rewrite_set_cookie_for_current_host($cookie), false);
        }
    }
    echo $result["body"];
    exit;
}

try {
    $requestPath = parse_url($_SERVER["REQUEST_URI"] ?? "/", PHP_URL_PATH);
    $queryString = $_SERVER["QUERY_STRING"] ?? "";
    $method = $_SERVER["REQUEST_METHOD"] ?? "GET";

    if ($requestPath === "/api/cloud-status") {
        $session = get_session_user($config);
        $providers = proxy_request(
            $config["NCB_AUTH_API_URL"] . "/providers?instance=" . rawurlencode($config["NCB_INSTANCE"]),
            "GET",
            [
                "X-Database-Instance: " . $config["NCB_INSTANCE"],
                "Authorization: Bearer " . $config["NCB_SECRET_KEY"]
            ]
        );
        $providersData = decoded_json_or_null($providers["body"]);
        send_json(200, [
            "instance" => $config["NCB_INSTANCE"],
            "session" => $session,
            "providers" => is_array($providersData) && isset($providersData["providers"]) ? $providersData["providers"] : [],
            "ready" => $config["NCB_INSTANCE"] !== "" && $config["NCB_SECRET_KEY"] !== ""
        ]);
    }

    if ($requestPath === "/api/auth-providers") {
        $url = $config["NCB_AUTH_API_URL"] . "/providers?instance=" . rawurlencode($config["NCB_INSTANCE"]);
        forward_response(proxy_request($url, "GET", [
            "X-Database-Instance: " . $config["NCB_INSTANCE"],
            "Authorization: Bearer " . $config["NCB_SECRET_KEY"]
        ]));
    }

    if (strpos($requestPath, "/api/auth/") === 0) {
        $suffix = substr($requestPath, strlen("/api/auth/"));
        $url = $config["NCB_AUTH_API_URL"] . "/" . $suffix . "?instance=" . rawurlencode($config["NCB_INSTANCE"]);
        $url = append_query($url, $queryString);
        $body = in_array($method, ["GET", "HEAD"], true) ? null : raw_request_body();
        forward_response(proxy_request($url, $method, array_merge(
            [
                "Content-Type: application/json",
                "X-Database-Instance: " . $config["NCB_INSTANCE"],
                "Authorization: Bearer " . $config["NCB_SECRET_KEY"],
                "Origin: " . request_origin()
            ],
            (($cookies = extract_auth_cookies($_SERVER["HTTP_COOKIE"] ?? "")) !== "") ? ["Cookie: " . $cookies] : []
        ), $body));
    }

    if (strpos($requestPath, "/api/data/") === 0 || strpos($requestPath, "/api/public-data/") === 0) {
        $isPublic = strpos($requestPath, "/api/public-data/") === 0;
        if ($isPublic && !in_array($method, ["GET", "POST"], true)) {
            send_json(405, ["error" => "Public data route only supports GET and POST."]);
        }

        $sessionUser = $isPublic ? null : get_session_user($config);
        if (!$isPublic && (!is_array($sessionUser) || empty($sessionUser["id"]))) {
            send_json(401, ["error" => "Unauthorized"]);
        }

        $prefix = $isPublic ? "/api/public-data/" : "/api/data/";
        $suffix = substr($requestPath, strlen($prefix));
        $body = in_array($method, ["GET", "HEAD", "DELETE"], true) ? "" : raw_request_body();
        $parsedBody = decoded_json_or_null($body);

        if (!$isPublic && $method === "POST" && strpos($suffix, "create/") === 0 && is_array($parsedBody)) {
            unset($parsedBody["user_id"]);
            $parsedBody["user_id"] = $sessionUser["id"];
            $body = json_encode($parsedBody);
        }
        if (!$isPublic && $method === "PUT" && is_array($parsedBody)) {
            unset($parsedBody["user_id"]);
            $body = json_encode($parsedBody);
        }

        $url = $config["NCB_DATA_API_URL"] . "/" . $suffix . "?Instance=" . rawurlencode($config["NCB_INSTANCE"]);
        $url = append_query($url, $queryString);
        $headers = upstream_headers($config, !$isPublic);
        forward_response(proxy_request($url, $method, $headers, $body === "" ? null : $body));
    }

    send_text(404, "Not found");
} catch (Throwable $error) {
    send_json(500, ["error" => $error->getMessage()]);
}
