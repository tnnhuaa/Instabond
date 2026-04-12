import os
from motor.motor_asyncio import AsyncIOMotorClient
from dotenv import load_dotenv

load_dotenv()

MONGO_URL = os.getenv("MONGO_URL", "mongodb://localhost:27017")
MONGO_DB_NAME = os.getenv("MONGO_DB_NAME", "instabond")

client = AsyncIOMotorClient(MONGO_URL)
db = client[MONGO_DB_NAME]
users_collection = db.users