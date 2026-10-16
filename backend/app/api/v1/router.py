"""Version 1 of the public API. Feature routers (accounts, transactions, ...) plug in here."""

from fastapi import APIRouter

from app.api.v1 import system

api_router = APIRouter()
api_router.include_router(system.router)
