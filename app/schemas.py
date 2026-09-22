from typing import Literal

from pydantic import BaseModel, Field, field_validator


class QueueJoinRequest(BaseModel):
    customer_name: str = Field(min_length=1, max_length=60)
    item_count: int = Field(ge=1, le=300)
    payment_method: Literal["upi", "card", "cash"]
    priority_type: Literal["regular", "assistance"] = "regular"

    @field_validator("customer_name")
    @classmethod
    def clean_name(cls, value: str) -> str:
        cleaned = " ".join(value.split())
        if not cleaned:
            raise ValueError("Customer name cannot be empty")
        return cleaned


class CounterStatusRequest(BaseModel):
    status: Literal["open", "closed"]

