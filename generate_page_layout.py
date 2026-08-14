#!/usr/bin/env python3
"""توليد quran_pages_layout.json — توزيع كلمات كل صفحة على أسطر المصحف.

المصدر: Quran.com API v4 (mushaf=1, QCF V2 layout) لكل صفحة من 1 إلى 604.
كل صفحة مقسّمة إلى أسطر (line_number 1..15) والكلمات تحمل إحداثياتها
(surah/ayah) لربط الصفحة بالقرآن المحلي. يُستخدم النص العثماني (text_qpc_hafs)
للعرض بالخط العثماني الموجود دون اعتماد على خطوط QCF الكبيرة.
"""
import json
import urllib.request
import concurrent.futures
import time

API_BASE = "https://api.quran.com/api/v4"
OUT = "app/src/main/assets/quran_pages_layout.json"

USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) urwah-page-layout/1.0"


WORD_FIELDS = "text_uthmani,text_qpc_hafs,code_v2,page_number,line_number"


def fetch_verses(page: int):
    url = f"{API_BASE}/verses/by_page/{page}?mushaf=1&language=en&words=true&word_fields={WORD_FIELDS}&per_page=100"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["verses"]


def build_page(page: int):
    """يجلب الصفحة والجارتين؛ الكلمات تُصفّى حسب page_number لأن 56 آية
    «يتيمة» معلّقة بصفحة مختلفة عن موضع كلماتها الفيزيائي (نمط mushaf-renderer)."""
    verses = fetch_verses(page)
    adjacent = []
    if page > 1:
        adjacent += fetch_verses(page - 1)
    if page < 604:
        adjacent += fetch_verses(page + 1)

    raw_words = []
    for v in verses + adjacent:
        for w in v["words"]:
            if w.get("page_number", page) != page:
                continue
            raw_words.append((v["verse_key"], w))

    raw_words.sort(key=lambda t: t[1].get("id", 0))

    seen_surahs = []
    words = []
    for verse_key, w in raw_words:
        surah = int(verse_key.split(":")[0])
        if surah not in seen_surahs:
            seen_surahs.append(surah)
        words.append({
            "text": w.get("text_qpc_hafs") or w.get("text_uthmani") or w.get("text", ""),
            "surah": surah,
            "ayah": int(verse_key.split(":")[1]),
            "line": int(w.get("line_number", 1)),
            "type": w.get("char_type_name", "word"),
            "code": w.get("code_v2", ""),
        })

    lines_map = {}
    for w in words:
        lines_map.setdefault(w["line"], []).append(w)

    ordered = []
    for ln in sorted(lines_map.keys()):
        ordered.append(lines_map[ln])

    return {
        "surahs": seen_surahs,
        "lines": ordered,
    }


def main():
    t0 = time.time()
    result = {"hafs": {}}
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as ex:
        futures = {ex.submit(build_page, p): p for p in range(1, 605)}
        done = 0
        for fut in concurrent.futures.as_completed(futures):
            p = futures[fut]
            try:
                result["hafs"][str(p)] = fut.result()
            except Exception as e:
                print(f"page {p} FAILED: {e}")
            done += 1
            if done % 50 == 0:
                print(f"... {done}/604 ({time.time()-t0:.0f}s)")
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False)
    size = __import__("os").path.getsize(OUT)
    print(f"done in {time.time()-t0:.0f}s -> {OUT} ({size/1024/1024:.2f} MB)")


if __name__ == "__main__":
    main()


if __name__ == "__main__":
    main()
