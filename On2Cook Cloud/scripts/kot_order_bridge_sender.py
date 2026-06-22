#!/usr/bin/env python3
"""
Send demo KOT/POS orderdetails payloads to the On2Cook Cloud order bridge.

Default behavior:
  1. Reset the server-side bridge order list.
  2. Send the first 3 orders together.
  3. Send the remaining orders one at a time every 60 seconds.

Usage:
  python kot_order_bridge_sender.py
  python kot_order_bridge_sender.py --server https://www.on2cook.net --interval 60
  python kot_order_bridge_sender.py --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Dict, Iterable, List


DEFAULT_SERVER_URL = os.environ.get("ON2COOK_SERVER_URL", "https://www.on2cook.net")
DEFAULT_TOKEN = os.environ.get("ON2COOK_KOT_TOKEN", "guest-kot-demo")


ORDER_RECIPES = [
    {
        "name": "VEGETABLE UPMA",
        "category": "Breakfast",
        "quantity_text": "800 g",
        "qty": 1,
        "price": "184.71",
        "customer": ("Test", "Test", "7760978358"),
        "order_type": "Delivery",
        "payment": "Zomato Pay",
        "source": "POS",
        "note": "Less oil",
    },
    {
        "name": "VEG HAKKA NOODLE",
        "category": "Noodles",
        "quantity_text": "600 g",
        "qty": 1,
        "price": "203.90",
        "customer": ("Rajesh", "Ahmedabad", "9099942020"),
        "order_type": "Takeaway",
        "payment": "PhonePe",
        "source": "POS",
        "note": "Ready for dispatch",
    },
    {
        "name": "PAAL PAYASAM",
        "category": "Dessert",
        "quantity_text": "1 tray",
        "qty": 1,
        "price": "233.55",
        "customer": ("Priya", "Test kitchen", "9099942021"),
        "order_type": "Dine In",
        "payment": "Cash",
        "source": "Manual",
        "note": "No garlic",
    },
    {
        "name": "SPINACH OMELETTE",
        "category": "Breakfast",
        "quantity_text": "500 g",
        "qty": 1,
        "price": "172.38",
        "customer": ("Amit", "Service road", "9099942022"),
        "order_type": "Delivery",
        "payment": "Other",
        "source": "POS",
        "note": "Extra seasoning",
    },
    {
        "name": "VEG BURGER PATTY",
        "category": "Snacks",
        "quantity_text": "700 g",
        "qty": 1,
        "price": "244.29",
        "customer": ("Vikram", "Counter pickup", "9099942023"),
        "order_type": "Takeaway",
        "payment": "Card",
        "source": "POS",
        "note": "Queue after lunch batch",
    },
    {
        "name": "CHI LEMON COR SP",
        "category": "Soup",
        "quantity_text": "2 bowls",
        "qty": 1,
        "price": "602.81",
        "customer": ("Sandy", "On2Cook demo", "9099942024"),
        "order_type": "Dine In",
        "payment": "Cash",
        "source": "Manual",
        "note": "Extra seasoning",
    },
    {
        "name": "ONION PAKODA",
        "category": "Starters",
        "quantity_text": "450 g",
        "qty": 1,
        "price": "198.10",
        "customer": ("Neha", "Kitchen gate", "9099942025"),
        "order_type": "Delivery",
        "payment": "UPI",
        "source": "POS",
        "note": "Priority pickup",
    },
]


def money(value: Decimal | str | float | int) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def make_order_payload(index: int, recipe: Dict[str, Any], token: str) -> Dict[str, Any]:
    order_no = 84 + index
    customer_name, customer_address, customer_phone = recipe["customer"]
    core_total = money(recipe["price"])
    discount_rate = Decimal("15") if index % 3 == 0 else Decimal("10")
    discount_total = money(core_total * discount_rate / Decimal("100"))
    taxable_total = money(core_total - discount_total)
    cgst = money(taxable_total * Decimal("0.025"))
    sgst = money(taxable_total * Decimal("0.025"))
    packaging_charge = money("0.95" if recipe["order_type"] != "Dine In" else "0")
    delivery_charge = money("2.00" if recipe["order_type"] == "Delivery" else "0")
    service_charge = money(taxable_total * Decimal("0.01") if recipe["order_type"] == "Dine In" else "0")
    total = money(taxable_total + cgst + sgst + packaging_charge + delivery_charge + service_charge)
    created_on = (datetime.now() + timedelta(minutes=index)).strftime("%Y-%m-%d %H:%M:%S")
    item_name = f"{recipe['name']} ({recipe['quantity_text']})"

    return {
        "token": token,
        "properties": {
            "Restaurant": {
                "res_name": "Android Live",
                "address": "Mexicans",
                "contact_information": "7060532398",
                "restID": "3n9tgu1d",
            },
            "Customer": {
                "name": customer_name,
                "address": customer_address,
                "phone": customer_phone,
                "gstin": "1234567890",
            },
            "Order": {
                "orderID": order_no,
                "customer_invoice_id": str(order_no),
                "delivery_charges": float(delivery_charge),
                "order_type": recipe["order_type"],
                "payment_type": "Other",
                "table_no": "12" if recipe["order_type"] == "Dine In" else "",
                "no_of_persons": 2 if recipe["order_type"] == "Dine In" else 0,
                "discount_total": float(discount_total),
                "tax_total": float(money(cgst + sgst)),
                "round_off": "0.00",
                "core_total": float(core_total),
                "total": float(total),
                "created_on": created_on,
                "order_from": recipe["source"],
                "order_from_id": "",
                "sub_order_type": recipe["order_type"],
                "packaging_charge": float(packaging_charge),
                "status": "Success",
                "token_no": "",
                "custom_payment_type": recipe["payment"],
                "comment": recipe["note"],
                "service_charge": float(service_charge),
                "biller": "biller (biller)",
                "assignee": "",
            },
            "Tax": [
                {"title": "CGST", "type": "P", "rate": 2.5, "amount": float(cgst)},
                {"title": "SGST", "type": "P", "rate": 2.5, "amount": float(sgst)},
            ],
            "Discount": [
                {
                    "title": "Customer Discount",
                    "type": "P",
                    "rate": float(discount_rate),
                    "amount": float(discount_total),
                }
            ],
            "OrderItem": [
                {
                    "name": item_name,
                    "itemid": 13477243 + index,
                    "itemcode": f"OC{order_no}",
                    "vendoritemcode": "",
                    "specialnotes": recipe["note"],
                    "price": float(core_total),
                    "quantity": recipe["qty"],
                    "total": float(core_total),
                    "addon": [],
                    "category_name": recipe["category"],
                    "sap_code": str(2106 + index),
                    "discount": float(discount_total),
                    "tax": float(money(cgst + sgst)),
                }
            ],
        },
        "event": "orderdetails",
    }


def bridge_url(server: str, suffix: str = "") -> str:
    return server.rstrip("/") + "/api/orders/bridge" + suffix


def post_json(url: str, payload: Dict[str, Any], timeout: int) -> Dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "On2Cook-KOT-Bridge/1.0",
            "X-On2Cook-Guest": "true",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code} from {url}: {body}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Could not reach {url}: {error}") from error


def send_batch(server: str, orders: Iterable[Dict[str, Any]], timeout: int, dry_run: bool) -> None:
    batch = list(orders)
    if dry_run:
        print(json.dumps({"orders": batch}, indent=2))
        return
    result = post_json(bridge_url(server), {"orders": batch, "source": "python_kot_bridge"}, timeout)
    print(f"Sent {len(batch)} order(s): accepted={result.get('accepted')} revision={result.get('revision')}")


def reset_bridge(server: str, timeout: int, dry_run: bool) -> None:
    if dry_run:
        print(f"DRY RUN reset: {bridge_url(server, '/reset')}")
        return
    result = post_json(bridge_url(server, "/reset"), {"guest": True}, timeout)
    print(f"Reset bridge: run_id={result.get('run_id')} revision={result.get('revision')}")


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Send On2Cook demo KOT orders to the server bridge.")
    parser.add_argument("--server", default=DEFAULT_SERVER_URL, help="On2Cook Cloud base URL.")
    parser.add_argument("--interval", type=int, default=60, help="Seconds between later orders.")
    parser.add_argument("--initial", type=int, default=3, help="How many orders to send immediately.")
    parser.add_argument("--limit", type=int, default=7, help="Total orders to send from the built-in list.")
    parser.add_argument("--timeout", type=int, default=30, help="HTTP timeout in seconds.")
    parser.add_argument("--token", default=DEFAULT_TOKEN, help="KOT token field to include in every payload.")
    parser.add_argument("--no-reset", action="store_true", help="Do not clear server orders before sending.")
    parser.add_argument("--dry-run", action="store_true", help="Print payloads without sending.")
    return parser.parse_args(argv)


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    limit = max(1, min(args.limit, len(ORDER_RECIPES)))
    initial = max(0, min(args.initial, limit))
    orders = [make_order_payload(index, recipe, args.token) for index, recipe in enumerate(ORDER_RECIPES[:limit])]

    print(f"On2Cook KOT bridge target: {args.server}")
    if not args.no_reset:
        reset_bridge(args.server, args.timeout, args.dry_run)

    if initial:
        send_batch(args.server, orders[:initial], args.timeout, args.dry_run)

    for index, order in enumerate(orders[initial:], start=initial + 1):
        if args.interval > 0:
            print(f"Waiting {args.interval} seconds before order {index}...")
            time.sleep(args.interval)
        send_batch(args.server, [order], args.timeout, args.dry_run)

    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
