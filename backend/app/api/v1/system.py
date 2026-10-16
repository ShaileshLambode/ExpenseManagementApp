from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/system", tags=["system"])


class SystemInfo(BaseModel):
    api_version: str


@router.get("/info", response_model=SystemInfo)
def info() -> SystemInfo:
    return SystemInfo(api_version="v1")
