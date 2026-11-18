# Atlas API Developer Guide

Atlas is the public REST API for Halvorsen Labs products. All requests are authenticated with an API key sent in the
X-Atlas-Key header. API keys expire and must be rotated every 90 days.

The default rate limit is 600 requests per minute per API key. Clients that exceed the limit receive HTTP 429 and should
retry after the number of seconds given in the Retry-After header.

List endpoints use cursor-based pagination: pass the next_cursor value from the previous response to fetch the next page.
Each page returns at most 200 items.

Atlas API v1 is deprecated and will be shut down on March 31, 2027. All clients should migrate to v2 before that date.
