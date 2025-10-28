
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


<img width="4519" height="4590" alt="IMG_5006" src="https://github.com/user-attachments/assets/b46f035a-8ba3-4cb5-bde6-4c5e4702da0b" />
