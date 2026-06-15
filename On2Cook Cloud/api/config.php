<?php

$defaults = [
    "NCB_INSTANCE" => "",
    "NCB_AUTH_API_URL" => "https://app.nocodebackend.com/api/user-auth",
    "NCB_DATA_API_URL" => "https://app.nocodebackend.com/api/data",
    "NCB_APP_URL" => "https://app.nocodebackend.com",
    "NCB_SECRET_KEY" => ""
];

$config = [];

$localConfig = __DIR__ . "/config.local.php";
if (file_exists($localConfig)) {
    $loaded = require $localConfig;
    if (is_array($loaded)) {
        $config = $loaded;
    }
}

foreach ($defaults as $key => $value) {
    $envValue = getenv($key);
    if ($envValue !== false && $envValue !== "") {
        $config[$key] = $envValue;
    }
}

return array_merge($defaults, $config);
