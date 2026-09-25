import hashlib
def encrypt_password(password: str) -> str:
    salt = "Γ_Κ-ζ.Τfkst"
    return hashlib.md5((password + salt).encode()).hexdigest()