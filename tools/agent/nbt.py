"""A minimal reader and writer for Minecraft's NBT, enough to edit a level.dat with no dependency.

A compound is a dict and a list is a `TagList`; every other value is a `Tag` holding its type id, so a
file read and written back is byte for byte the same. Only the gzip-compressed form, which level.dat
and the files under data/ use.
"""

import gzip
import struct
from dataclasses import dataclass, field

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)

_SCALARS = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}
_ARRAYS = {BYTE_ARRAY: ">b", INT_ARRAY: ">i", LONG_ARRAY: ">q"}


@dataclass
class Tag:
    """A value other than a compound or a list, with the type it is written as."""
    type: int
    value: object


@dataclass
class TagList:
    """A list, with the type of its elements (END when empty)."""
    type: int
    items: list = field(default_factory=list)


def byte(value):
    return Tag(BYTE, int(value))


def integer(value):
    return Tag(INT, int(value))


def string(value):
    return Tag(STRING, str(value))


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, fmt):
        size = struct.calcsize(fmt)
        (value,) = struct.unpack_from(fmt, self.data, self.pos)
        self.pos += size
        return value

    def string(self):
        length = self.take(">H")
        raw = self.data[self.pos:self.pos + length]
        self.pos += length
        # Modified UTF-8, which only differs from UTF-8 for NUL and characters outside the BMP.
        return raw.decode("utf-8", errors="surrogatepass")

    def payload(self, kind):
        if kind in _SCALARS:
            return Tag(kind, self.take(_SCALARS[kind]))
        if kind in _ARRAYS:
            length = self.take(">i")
            return Tag(kind, [self.take(_ARRAYS[kind]) for _ in range(length)])
        if kind == STRING:
            return Tag(kind, self.string())
        if kind == LIST:
            element = self.take(">b")
            length = self.take(">i")
            return TagList(element, [self.payload(element) for _ in range(length)])
        if kind == COMPOUND:
            result = {}
            while True:
                child = self.take(">b")
                if child == END:
                    return result
                name = self.string()
                result[name] = self.payload(child)
        raise ValueError(f"unknown NBT tag type {kind} at byte {self.pos}")


class _Writer:
    def __init__(self):
        self.parts = []

    def put(self, fmt, value):
        self.parts.append(struct.pack(fmt, value))

    def string(self, value):
        raw = value.encode("utf-8", errors="surrogatepass")
        self.put(">H", len(raw))
        self.parts.append(raw)

    def payload(self, value):
        if isinstance(value, dict):
            for name, child in value.items():
                self.put(">b", _kind(child))
                self.string(name)
                self.payload(child)
            self.put(">b", END)
        elif isinstance(value, TagList):
            self.put(">b", value.type)
            self.put(">i", len(value.items))
            for item in value.items:
                self.payload(item)
        elif value.type in _SCALARS:
            self.put(_SCALARS[value.type], value.value)
        elif value.type in _ARRAYS:
            self.put(">i", len(value.value))
            for item in value.value:
                self.put(_ARRAYS[value.type], item)
        elif value.type == STRING:
            self.string(value.value)
        else:
            raise ValueError(f"cannot write NBT tag type {value.type}")


def _kind(value):
    if isinstance(value, dict):
        return COMPOUND
    if isinstance(value, TagList):
        return LIST
    return value.type


def load(path):
    """The root compound of a gzip-compressed NBT file, and the root's name."""
    with gzip.open(path, "rb") as f:
        reader = _Reader(f.read())
    if reader.take(">b") != COMPOUND:
        raise ValueError(f"{path} does not start with a compound")
    name = reader.string()
    return reader.payload(COMPOUND), name


def save(path, root, name=""):
    writer = _Writer()
    writer.put(">b", COMPOUND)
    writer.string(name)
    writer.payload(root)
    with gzip.open(path, "wb") as f:
        f.write(b"".join(writer.parts))
