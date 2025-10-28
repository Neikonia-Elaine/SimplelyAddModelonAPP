
## Setup

```bash
cd Server
docker build -t notes-app-ldap:latest .
cd ../Docker
docker build -t notes-ml:latest -f Dockerfile.ml .
docker compose up
```

⏳ Wait a few minutes for the model to load...

Then run the Android app.
