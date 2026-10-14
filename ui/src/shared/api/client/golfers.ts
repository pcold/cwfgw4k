import type { CreateGolferRequest, Golfer } from '../types';
import { getJson, postJson } from './core';

export const golfersApi = {
  golfers: (search?: string): Promise<Golfer[]> => {
    const params = new URLSearchParams();
    if (search && search.trim().length > 0) params.set('search', search.trim());
    const qs = params.toString();
    return getJson<Golfer[]>(`/golfers${qs ? `?${qs}` : ''}`);
  },
  createGolfer: (input: CreateGolferRequest): Promise<Golfer> => postJson<Golfer>('/golfers', input),
};
