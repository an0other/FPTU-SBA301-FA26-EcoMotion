import { useEffect, useState } from "react";
import { paymentService } from "../services/paymentService";

export function usePaymentHistory(page, size) {
  const [state, setState] = useState({
    items: [],
    total: 0,
    hasMore: false,
    loading: true,
    error: "",
  });
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setState({
        items: [],
        total: 0,
        hasMore: false,
        loading: true,
        error: "",
      });

      try {
        const result = await paymentService.getHistory({ page, size });

        if (active) {
          setState({
            items: result.items,
            total: result.total,
            hasMore: result.hasMore,
            loading: false,
            error: "",
          });
        }
      } catch (error) {
        if (active) {
          setState({
            items: [],
            total: 0,
            hasMore: false,
            loading: false,
            error: error.message || "Không tải được lịch sử thanh toán.",
          });
        }
      }
    }

    load();

    return () => {
      active = false;
    };
  }, [page, size, revision]);

  return {
    ...state,
    retry: () => setRevision((value) => value + 1),
  };
}
