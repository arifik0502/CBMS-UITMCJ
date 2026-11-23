import { lazy, Suspense } from "react";
import { Route, Routes } from "react-router-dom";
import { isConfigured } from "./lib/supabase";
import { CartProvider } from "./lib/cart";
import { SiteConfigProvider } from "./lib/SiteConfigContext";
import { Header, LinkButton, LoadingScreen, Shell } from "./components/ui";
import Home from "./pages/Home";
const OrderPage = lazy(() => import("./pages/Order"));
const OrdersPage = lazy(() => import("./pages/Orders"));
const SupportPage = lazy(() => import("./pages/Support"));
const PayPage = lazy(() => import("./pages/Pay"));
const TrackPage = lazy(() => import("./pages/Track"));

function NotFound() {
  return (
    <Shell>
      <Header />
      <div className="space-y-4 py-16 text-center">
        <p className="font-display text-3xl font-extrabold">Page not found</p>
        <p className="text-sm font-medium">Check the link, or start from the menu.</p>
        <LinkButton to="/">Go to the menu</LinkButton>
      </div>
    </Shell>
  );
}

function SetupNeeded() {
  return (
    <Shell>
      <Header />
      <div className="ticket space-y-3 p-5">
        <p className="font-display text-2xl font-extrabold">Connect the backend</p>
        <p className="text-sm">
          Set <code className="font-bold">VITE_SUPABASE_URL</code> and{" "}
          <code className="font-bold">VITE_SUPABASE_ANON_KEY</code> (see <code>.env.example</code>), then restart.
        </p>
      </div>
    </Shell>
  );
}

export default function App() {
  if (!isConfigured) return <SetupNeeded />;
  return (
    <SiteConfigProvider>
    <CartProvider>
      <Suspense fallback={<Shell><LoadingScreen /></Shell>}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/order" element={<OrderPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/support" element={<SupportPage />} />
          <Route path="/pay/:token" element={<PayPage />} />
          <Route path="/track/:token" element={<TrackPage />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </CartProvider>
    </SiteConfigProvider>
  );
}


