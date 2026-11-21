import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { addLine, itemCount, parseCart, removeLine, setLineQuantity, type CartLine } from "./cartLogic";
import { safeGet, safeSet } from "./storage";

const CART_KEY = "cbms:cart:v1";

interface CartApi {
  lines: CartLine[];
  count: number;
  quantityOf: (productId: string) => number;
  add: (productId: string, max: number, qty?: number) => void;
  setQuantity: (productId: string, qty: number, max: number) => void;
  remove: (productId: string) => void;
  replace: (lines: CartLine[]) => void;
  clear: () => void;
  drawerOpen: boolean;
  openDrawer: () => void;
  closeDrawer: () => void;
}

const CartContext = createContext<CartApi | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>(() => parseCart(safeGet(CART_KEY)));
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => { safeSet(CART_KEY, JSON.stringify(lines)); }, [lines]);

  // Keep several open tabs in sync.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === CART_KEY) setLines(parseCart(e.newValue));
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const add = useCallback((productId: string, max: number, qty = 1) =>
    setLines((l) => addLine(l, productId, qty, max)), []);
  const setQuantity = useCallback((productId: string, qty: number, max: number) =>
    setLines((l) => setLineQuantity(l, productId, qty, max)), []);
  const remove = useCallback((productId: string) => setLines((l) => removeLine(l, productId)), []);
  const replace = useCallback((next: CartLine[]) => setLines(next), []);
  const clear = useCallback(() => setLines([]), []);
  const openDrawer = useCallback(() => setDrawerOpen(true), []);
  const closeDrawer = useCallback(() => setDrawerOpen(false), []);

  const value = useMemo<CartApi>(() => ({
    lines,
    count: itemCount(lines),
    quantityOf: (id) => lines.find((l) => l.productId === id)?.quantity ?? 0,
    add, setQuantity, remove, replace, clear, drawerOpen, openDrawer, closeDrawer
  }), [lines, add, setQuantity, remove, replace, clear, drawerOpen, openDrawer, closeDrawer]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartApi {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used inside <CartProvider>");
  return ctx;
}
