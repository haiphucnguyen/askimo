---
title: Blog
nav_order: 80
has_children: true
permalink: /blog/
---

# 📰 Askimo Blog

{% for post in site.posts %}
- [{{ post.title }}]({{ post.url | relative_url }}) <small>— {{ post.date | date: "%b %d, %Y" }}</small>
  {% endfor %}
